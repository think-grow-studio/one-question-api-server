package site.one_question.api.question.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.question.domain.exception.CandidateNotFoundException;
import site.one_question.api.question.domain.exception.DailyQuestionNotFoundException;
import site.one_question.api.question.domain.exception.QuestionNotFoundException;

@Service
@RequiredArgsConstructor
public class DailyQuestionService {

    private final QuestionRepository questionRepository;
    private final DailyQuestionRepository dailyQuestionRepository;
    private final DailyQuestionCandidateRepository dailyQuestionCandidateRepository;

    public Optional<DailyQuestion> findByMemberIdAndDate(Long memberId, LocalDate date) {
        return dailyQuestionRepository.findByMemberIdAndDate(memberId, date);
    }

    public DailyQuestion findByMemberIdAndDateOrThrow(Long memberId, LocalDate date) {
        return dailyQuestionRepository.findByMemberIdAndDate(memberId, date)
            .orElseThrow(DailyQuestionNotFoundException::new);
    }

    /**
     * 사이클 내에서 아직 제공되지 않은 질문 중 랜덤으로 하나를 선택한다.
     * fallback 1: served만 제외 (사이클 내 후보 중복 허용)
     * fallback 2: 전체 질문에서 랜덤 선택 (사이클 중복 허용)
     *
     * @param cycle 질문 사이클 도메인
     * @return 랜덤으로 선택된 질문
     * @throws QuestionNotFoundException 활성화된 질문이 없는 경우
     */
    public Question selectRandomQuestion(QuestionCycle cycle) {
        List<Long> servedQInCycle = dailyQuestionRepository.findQuestionIdsByCycleId(cycle.getId());
        List<Long> candidateQInCycle = dailyQuestionCandidateRepository.findQuestionIdsByCycleId(cycle.getId());

        Set<Long> allExcluded = new HashSet<>(servedQInCycle);
        allExcluded.addAll(candidateQInCycle);

        List<Question> questionPoll = questionRepository.findAllByStatusAndIdNotIn(
            QuestionStatus.ACTIVE, new ArrayList<>(allExcluded));

        if (questionPoll.isEmpty()) {
            // fallback 1: 사이클 내 후보 중복 허용, served만 제외
            questionPoll = questionRepository.findAllByStatusAndIdNotIn(
                QuestionStatus.ACTIVE, servedQInCycle);
        }

        if (questionPoll.isEmpty()) {
            // fallback 2: 사이클 중복 허용, 전체에서 랜덤
            questionPoll = questionRepository.findAllByStatus(QuestionStatus.ACTIVE);
        }

        if (questionPoll.isEmpty()) {
            throw new QuestionNotFoundException();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(questionPoll.size());
        return questionPoll.get(randomIndex);
    }

    public DailyQuestion save(DailyQuestion dailyQuestion) {
        return dailyQuestionRepository.save(dailyQuestion);
    }

    /**
     * 리로드 시 새로운 후보 질문을 선택한다.
     * 제외 우선순위: (1) 사이클 내 제공된 질문 + excludeQuestionIds + 사이클 내 기존 후보 질문
     * fallback: 사이클 중복 허용, excludeQuestionIds만 제외
     *
     * @param cycle 질문 사이클 도메인
     * @param excludeQuestionIds 제외할 질문 ID 목록 (오늘 현재 후보 질문 전체)
     * @return 랜덤으로 선택된 질문
     * @throws QuestionNotFoundException 선택 가능한 질문이 없는 경우
     */
    public Question selectRandomQuestionExcluding(QuestionCycle cycle, List<Long> excludeQuestionIds) {
        List<Long> servedQInCycle = dailyQuestionRepository.findQuestionIdsByCycleId(cycle.getId());
        List<Long> candidateQInCycle = dailyQuestionCandidateRepository.findQuestionIdsByCycleId(cycle.getId());

        Set<Long> allExcluded = new HashSet<>(servedQInCycle);
        allExcluded.addAll(candidateQInCycle);
        allExcluded.addAll(excludeQuestionIds);

        List<Question> questionPoll = questionRepository.findAllByStatusAndIdNotIn(
            QuestionStatus.ACTIVE, new ArrayList<>(allExcluded));

        if (questionPoll.isEmpty()) {
            // fallback 1: 사이클 후보 중복 허용, served만 제외
            questionPoll = questionRepository.findAllByStatusAndIdNotIn(
                QuestionStatus.ACTIVE, servedQInCycle);
        }

        if (questionPoll.isEmpty()) {
            // fallback 2: 사이클 중복 허용, 오늘 후보만 제외
            questionPoll = questionRepository.findAllByStatusAndIdNotIn(
                QuestionStatus.ACTIVE, excludeQuestionIds);
        }

        if (questionPoll.isEmpty()) {
            throw new QuestionNotFoundException();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(questionPoll.size());
        return questionPoll.get(randomIndex);
    }

    public List<DailyQuestion> findByMemberIdAndDateBetween(
            Long memberId, LocalDate startDate, LocalDate endDate) {
        return dailyQuestionRepository.findByMemberIdAndDateBetween(memberId, startDate, endDate);
    }

    public List<DailyQuestionCandidate> findCandidatesByDailyQuestion(DailyQuestion dailyQuestion) {
        return dailyQuestionCandidateRepository.findAllByDailyQuestionOrderByReceivedOrderAsc(dailyQuestion);
    }

    public List<DailyQuestionCandidate> findCandidatesByDailyQuestionIds(List<Long> dailyQuestionIds) {
        return dailyQuestionCandidateRepository.findAllByDailyQuestionIdInOrderByReceivedOrder(dailyQuestionIds);
    }

    public DailyQuestionCandidate findCandidateOrThrow(DailyQuestion dailyQuestion, Long questionId) {
        return dailyQuestionCandidateRepository.findByDailyQuestionAndQuestionId(dailyQuestion, questionId)
            .orElseThrow(CandidateNotFoundException::new);
    }

    public DailyQuestionCandidate saveCandidate(DailyQuestion dailyQuestion, Question question, int order) {
        return dailyQuestionCandidateRepository.save(
            DailyQuestionCandidate.create(dailyQuestion, question, order));
    }

    public void deleteCandidatesBy(DailyQuestion dailyQuestion) {
        dailyQuestionCandidateRepository.deleteByDailyQuestion(dailyQuestion);
    }

    public void deleteByMemberId(Long memberId) {
        dailyQuestionRepository.deleteByMemberId(memberId);
    }
}
