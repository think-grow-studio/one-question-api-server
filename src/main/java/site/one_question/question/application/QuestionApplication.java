package site.one_question.question.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberService;
import site.one_question.question.domain.DatePolicy;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.DailyQuestionAnswerService;
import site.one_question.question.domain.DailyQuestionService;
import site.one_question.question.domain.exception.AlreadyAnsweredException;
import site.one_question.question.domain.exception.ReloadLimitExceededException;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.question.domain.QuestionCycleService;
import site.one_question.question.domain.HistoryDirection;
import site.one_question.question.presentation.response.GetQuestionHistoryResponse;
import site.one_question.question.presentation.response.QuestionHistoryItemDto;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;

@Service
@Transactional
@RequiredArgsConstructor
public class QuestionApplication {

    private final DailyQuestionService dailyQuestionService;
    private final DailyQuestionAnswerService answerService;
    private final QuestionCycleService cycleService;
    private final MemberService memberService;
    private final DatePolicy datePolicy;

    public ServeDailyQuestionResponse serveDailyQuestion(Long memberId, LocalDate date, String timezone) {
        // 멱등성: 기존 질문 있으면 반환
        Optional<DailyQuestion> existing = dailyQuestionService.findByMemberIdAndDate(memberId, date);
        if (existing.isPresent()) {
            DailyQuestion dailyQuestion = existing.get();
            return ServeDailyQuestionResponse.from(dailyQuestion, dailyQuestion.getQuestion(), dailyQuestion.getQuestionCycle());
        }

        Member member = memberService.findById(memberId);
        QuestionCycle cycle = cycleService.getOrCreateCycle(member, date, timezone);
        Question selectedQuestion = dailyQuestionService.selectRandomQuestion(cycle);

        // 5. DailyQuestion 생성 및 저장
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                selectedQuestion,
                date,
                timezone
        );
        DailyQuestion saved = dailyQuestionService.save(dailyQuestion);

        return ServeDailyQuestionResponse.from(saved, selectedQuestion, cycle);
    }

    public ServeDailyQuestionResponse reloadDailyQuestion(Long memberId, LocalDate date, String timezone) {
        // 1. 오늘의 DailyQuestion 조회
        DailyQuestion dailyQuestion = dailyQuestionService.findByMemberIdAndDateOrThrow(memberId, date);

        // 2. 답변 여부 확인
        if (answerService.hasAnswer(dailyQuestion)) {
            throw new AlreadyAnsweredException();
        }

        // 3. 변경 가능 여부 확인
        if (!dailyQuestion.canChangeQuestion()) {
            throw new ReloadLimitExceededException(
                dailyQuestion.getMember().getPermission().getMaxQuestionChangeCount());
        }

        // 4. 새 질문 선택 (현재 질문 제외)
        Question newQuestion = dailyQuestionService.selectRandomQuestionExcluding(
            dailyQuestion.getQuestionCycle(),
            dailyQuestion.getQuestion()
        );

        // 5. 질문 변경
        dailyQuestion.changeQuestion(newQuestion);

        // 6. 응답 반환
        return ServeDailyQuestionResponse.from(dailyQuestion, newQuestion, dailyQuestion.getQuestionCycle());
    }

    @Transactional(readOnly = true)
    public GetQuestionHistoryResponse getQuestionHistory(
            Long memberId, LocalDate baseDate, HistoryDirection historyDirection, int size, String timezone) {

        Member member = memberService.findById(memberId);
        LocalDate today = DatePolicy.getToday(timezone);

        DatePolicy.requireNotFuture(baseDate, timezone);
        LocalDate joinedDate = member.getJoinedDate();

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
                int half = size / 2; // size는 홀수 이어야만 합니다.
                startDate = baseDate.minusDays(half);
                endDate = baseDate.plusDays(half);
            }
            default -> {
                startDate = baseDate;
                endDate = baseDate;
            }
        }

        // 범위 제한: joinedDate 이상, today 이하
        if (startDate.isBefore(joinedDate)) {
            startDate = joinedDate;
        }
        if (endDate.isAfter(today)) {
            endDate = today;
        }

        // DailyQuestion 애그리거트 루트 조회
        List<DailyQuestion> dailyQuestions = dailyQuestionService.findByMemberIdAndDateBetween(
            memberId, startDate, endDate);

        // DailyQuestion을 날짜 기준 Map으로 변환
        Map<LocalDate, DailyQuestion> dailyQuestionMap = dailyQuestions.stream()
            .collect(Collectors.toMap(DailyQuestion::getDate, Function.identity()));

        // 날짜별 히스토리 아이템 생성 (최신순)
        List<QuestionHistoryItemDto> histories = new ArrayList<>();
        LocalDate currentDate = endDate;
        while (!currentDate.isBefore(startDate)) {
            DailyQuestion dq = dailyQuestionMap.get(currentDate);

            if (dq == null) {
                histories.add(QuestionHistoryItemDto.noQuestion(currentDate));
            } else {
                histories.add(QuestionHistoryItemDto.from(dq, timezone));
            }
            currentDate = currentDate.minusDays(1);
        }

        // 페이징 정보 계산
        boolean hasPrevious = startDate.isAfter(joinedDate);
        boolean hasNext = endDate.isBefore(today);

        return new GetQuestionHistoryResponse(histories, hasPrevious, hasNext, startDate, endDate);
    }
}
