package site.one_question.api.question.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.question.domain.DatePolicy;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswerService;
import site.one_question.api.question.domain.DailyQuestionCandidate;
import site.one_question.api.question.domain.DailyQuestionCandidateRepository;
import site.one_question.api.question.domain.QuestionLike;
import site.one_question.api.question.domain.QuestionLikeService;
import site.one_question.api.question.domain.QuestionService;
import site.one_question.api.question.domain.DailyQuestionService;
import site.one_question.api.question.domain.exception.AlreadyAnsweredException;
import site.one_question.api.question.domain.exception.ReloadLimitExceededException;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.QuestionCycleService;
import site.one_question.api.question.domain.HistoryDirection;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.exception.AnswerAlreadyExistsException;
import site.one_question.api.question.domain.exception.AnswerNotFoundException;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostService;
import site.one_question.api.question.presentation.response.CreateAnswerResponse;
import site.one_question.api.question.presentation.response.CheckCandidateCycleResponse;
import site.one_question.api.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.api.question.presentation.response.QuestionHistoryItemDto;
import site.one_question.api.question.presentation.response.ServeDailyQuestionResponse;
import site.one_question.api.question.presentation.response.ToggleLikeResponse;
import site.one_question.api.question.presentation.response.UpdateAnswerResponse;

@Service
@Transactional
@RequiredArgsConstructor
public class QuestionApplication {

    private final DailyQuestionService dailyQuestionService;
    private final DailyQuestionAnswerService answerService;
    private final QuestionCycleService cycleService;
    private final MemberService memberService;
    private final AnswerPostService answerPostService;
    private final QuestionLikeService questionLikeService;
    private final QuestionService questionService;
    private final DailyQuestionCandidateRepository candidateRepository;

    public ServeDailyQuestionResponse serveDailyQuestion(Long memberId, LocalDate date, String timezone) {
        // 멱등성: 기존 질문 있으면 반환
        Optional<DailyQuestion> existing = dailyQuestionService.findByMemberIdAndDate(memberId, date);
        if (existing.isPresent()) {
            DailyQuestion dailyQuestion = existing.get();
            List<DailyQuestionCandidate> candidates =
                dailyQuestionService.findCandidatesByDailyQuestion(dailyQuestion);
            Set<Long> likedIds = questionLikeService.findLikedQuestionIdsByMember(
                List.of(dailyQuestion.getQuestion().getId()), memberId);
            return ServeDailyQuestionResponse.from(dailyQuestion, candidates, likedIds);
        }

        Member member = memberService.findById(memberId);
        QuestionCycle cycle = cycleService.getOrCreateCycle(member, date, timezone);
        Question selectedQuestion = dailyQuestionService.selectRandomQuestion(cycle);

        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                selectedQuestion,
                date,
                timezone
        );
        DailyQuestion saved = dailyQuestionService.save(dailyQuestion);

        DailyQuestionCandidate initial = dailyQuestionService.saveCandidate(saved, selectedQuestion, 1);

        List<Long> questionIds = List.of(selectedQuestion.getId());
        Set<Long> likedIds = questionLikeService.findLikedQuestionIdsByMember(questionIds, memberId);
        return ServeDailyQuestionResponse.from(saved, List.of(initial), likedIds);
    }

    public ServeDailyQuestionResponse reloadDailyQuestion(Long memberId, LocalDate date, String timezone) {
        // 1. 오늘의 DailyQuestion 조회
        DailyQuestion dailyQuestion = dailyQuestionService.findByMemberIdAndDateOrThrow(memberId, date);

        // 2. 답변 여부 확인
        if (dailyQuestion.hasAnswer()) {
            throw new AlreadyAnsweredException();
        }

        // 3. 변경 가능 여부 확인
        if (!dailyQuestion.canChangeQuestion()) {
            throw new ReloadLimitExceededException(
                dailyQuestion.getMember().getPermission().getMaxQuestionChangeCount());
        }

        // 4. 기존 후보 전체 조회
        List<DailyQuestionCandidate> candidates =
            dailyQuestionService.findCandidatesByDailyQuestion(dailyQuestion);
        List<Long> candidateIds = candidates.stream()
            .map(c -> c.getQuestion().getId()).collect(Collectors.toList());

        // 5. 기존 후보 제외하고 새 질문 선택
        Question newQuestion = dailyQuestionService.selectRandomQuestionExcluding(
            dailyQuestion.getQuestionCycle(),
            candidateIds
        );

        // 6. 새 후보 저장
        int nextOrder = candidates.size() + 1;
        DailyQuestionCandidate newCandidate = dailyQuestionService.saveCandidate(dailyQuestion, newQuestion, nextOrder);

        // 7. 질문 변경 (자동 선택 + changeCount++)
        dailyQuestion.changeQuestion(newQuestion);

        // 8. 응답 반환 (기존 후보 + 새 후보 in-memory 합산)
        List<DailyQuestionCandidate> allCandidates = new ArrayList<>(candidates);
        allCandidates.add(newCandidate);
        Set<Long> likedIds = questionLikeService.findLikedQuestionIdsByMember(
            List.of(newQuestion.getId()), memberId);
        return ServeDailyQuestionResponse.from(dailyQuestion, allCandidates, likedIds);
    }

    public ServeDailyQuestionResponse selectQuestion(Long memberId, LocalDate date, Long questionId) {
        DailyQuestion dailyQuestion = dailyQuestionService.findByMemberIdAndDateOrThrow(memberId, date);

        if (dailyQuestion.hasAnswer()) {
            throw new AlreadyAnsweredException();
        }

        // 후보 목록에 있는지 검증
        DailyQuestionCandidate candidate = dailyQuestionService.findCandidateOrThrow(dailyQuestion, questionId);

        Question question = candidate.getQuestion();
        dailyQuestion.selectQuestion(question);

        List<DailyQuestionCandidate> allCandidates =
            dailyQuestionService.findCandidatesByDailyQuestion(dailyQuestion);
        Set<Long> likedIds = questionLikeService.findLikedQuestionIdsByMember(
            List.of(question.getId()), memberId);
        return ServeDailyQuestionResponse.from(dailyQuestion, allCandidates, likedIds);
    }

    @Transactional(readOnly = true)
    public CheckCandidateCycleResponse checkCandidateCycle(Long memberId, LocalDate date, Long questionId) {
        DailyQuestion dailyQuestion = dailyQuestionService.findByMemberIdAndDateOrThrow(memberId, date);

        // 오늘 후보에 실제 포함된 질문만 확인 대상이다.
        dailyQuestionService.findCandidateOrThrow(dailyQuestion, questionId);

        List<LocalDate> assignedDates = dailyQuestionService.findAssignedDatesInCycleExcluding(
            dailyQuestion.getQuestionCycle(), questionId, date
        );
        return CheckCandidateCycleResponse.from(assignedDates);
    }

    @Transactional(readOnly = true)
    public GetQuestionHistoryResponse getQuestionHistory(
            Long memberId, LocalDate baseDate, HistoryDirection historyDirection, int size, String timezone) {

        Member member = memberService.findById(memberId);
        QuestionCycle cycle = cycleService.getFirstCycle(member.getId());
        LocalDate today = DatePolicy.getToday(timezone);

        DatePolicy.requireNotFuture(baseDate, timezone);
        LocalDate cycleStartDate = cycle.getStartDate();

        // 날짜 범위 계산
        LocalDate startDate;
        LocalDate endDate;

        switch (historyDirection) {
            case PREVIOUS -> {
                startDate = baseDate.minusDays(size - 1);
                endDate = baseDate;
            }
            case NEXT -> {
                startDate = baseDate;
                endDate = baseDate.plusDays(size - 1);
            }
            case BOTH -> {
                int adjustedSize = size % 2 == 0 ? size - 1 : size; // 짝수면 -1하여 홀수로 변경
                int half = adjustedSize / 2;
                startDate = baseDate.minusDays(half);
                endDate = baseDate.plusDays(half);
            }
            default -> {
                startDate = baseDate;
                endDate = baseDate;
            }
        }

        // 범위 제한: cycleStartDate 이상, today 이하
        if (startDate.isBefore(cycleStartDate)) {
            startDate = cycleStartDate;
        }
        if (endDate.isAfter(today)) {
            endDate = today;
        }

        // DailyQuestion 애그리거트 루트 조회
        List<DailyQuestion> dailyQuestions = dailyQuestionService.findByMemberIdAndDateBetween(
            memberId, startDate, endDate);

        // UNANSWERED dailyQuestion ID 배치 수집
        List<Long> unansweredIds = dailyQuestions.stream()
            .filter(dq -> !dq.hasAnswer())
            .map(DailyQuestion::getId)
            .collect(Collectors.toList());

        // UNANSWERED candidates 배치 조회 및 그룹화
        Map<Long, List<DailyQuestionCandidate>> dqIdWithCandidates;
        if (unansweredIds.isEmpty()) {
            dqIdWithCandidates = Map.of();
        } else {
            dqIdWithCandidates = dailyQuestionService.findCandidatesByDailyQuestionIds(unansweredIds)
                .stream()
                .collect(Collectors.groupingBy(c -> c.getDailyQuestion().getId()));
        }

        // 좋아요 배치 조회: 각 날짜의 선택된 질문 ID만 (후보 질문 liked 불필요)
        List<Long> selectedQuestionIds = dailyQuestions.stream()
            .map(dq -> dq.getQuestion().getId())
            .collect(Collectors.toList());
        Set<Long> likedQuestionIds = questionLikeService.findLikedQuestionIdsByMember(selectedQuestionIds, memberId);

        // DailyQuestion을 날짜 기준 Map으로 변환
        Map<LocalDate, DailyQuestion> dateWithQuestions = dailyQuestions.stream()
            .collect(Collectors.toMap(DailyQuestion::getQuestionDate, Function.identity()));

        // 날짜별 히스토리 아이템 생성 (최신순)
        List<QuestionHistoryItemDto> histories = new ArrayList<>();
        LocalDate currentDate = endDate;
        while (!currentDate.isBefore(startDate)) {
            DailyQuestion dq = dateWithQuestions.get(currentDate);

            if (dq == null) {
                histories.add(QuestionHistoryItemDto.noQuestion(currentDate));
            } else {
                boolean liked = likedQuestionIds.contains(dq.getQuestion().getId());
                List<DailyQuestionCandidate> candidates = dqIdWithCandidates.getOrDefault(dq.getId(), List.of());
                histories.add(QuestionHistoryItemDto.from(dq, timezone, liked, candidates));
            }
            currentDate = currentDate.minusDays(1);
        }

        // 페이징 정보 계산
        boolean hasPrevious = startDate.isAfter(cycleStartDate);
        boolean hasNext = endDate.isBefore(today);

        return new GetQuestionHistoryResponse(histories, hasPrevious, hasNext, startDate, endDate);
    }

    public CreateAnswerResponse createAnswer(Long memberId, LocalDate date, String content, boolean publish, String timezone) {
        // 1. DailyQuestion 조회
        DailyQuestion dailyQuestion = dailyQuestionService.findByMemberIdAndDateOrThrow(memberId, date);

        // 2. 기존 답변 존재 여부 확인
        if (dailyQuestion.hasAnswer()) {
            throw new AnswerAlreadyExistsException(dailyQuestion.getId());
        }

        // 3. 회원 조회
        Member member = memberService.findById(memberId);

        // 4. Answer 생성 및 저장
        DailyQuestionAnswer answer = DailyQuestionAnswer.create(dailyQuestion, member, content, timezone);
        DailyQuestionAnswer saved = answerService.save(answer);

        // 5. 후보 삭제
        dailyQuestionService.deleteCandidatesBy(dailyQuestion);

        // 6. 공개 게시 요청 시 AnswerPost 생성
        if (publish) {
            answerPostService.publishOrCreate(saved, member);
        }

        // 6. 응답 반환
        return CreateAnswerResponse.from(saved, timezone, publish);
    }

    public ToggleLikeResponse toggleLike(Long memberId, Long questionId) {
        Question question = questionService.findByIdOrThrow(questionId);
        Member member = memberService.findById(memberId);
        var existingLike = questionLikeService.findByQuestionAndMember(question, member);

        boolean liked;
        if (existingLike.isPresent()) {
            questionLikeService.delete(existingLike.get());
            liked = false;
        } else {
            questionLikeService.save(QuestionLike.create(question, member));
            liked = true;
        }
        return new ToggleLikeResponse(liked);
    }

    public UpdateAnswerResponse updateAnswer(Long memberId, LocalDate date, String content, Boolean publish, String timezone) {
        DailyQuestion dailyQuestion = dailyQuestionService.findByMemberIdAndDateOrThrow(memberId, date);

        if (!dailyQuestion.hasAnswer()) {
            throw new AnswerNotFoundException(dailyQuestion.getId());
        }
        DailyQuestionAnswer answer = dailyQuestion.getAnswer();
        answer.updateContent(content);

        if (Objects.equals(Boolean.TRUE, publish)) {
            Member member = memberService.findById(memberId);
            answerPostService.publishOrCreate(answer, member);
        } else if (Boolean.FALSE.equals(publish)) {
            answerPostService.findByQuestionAnswer(answer).ifPresent(AnswerPost::unpublish);
        }

        boolean published = answerPostService.findByQuestionAnswer(answer)
                .map(AnswerPost::isPublished)
                .orElse(false);

        return UpdateAnswerResponse.from(answer, timezone, published);
    }
}
