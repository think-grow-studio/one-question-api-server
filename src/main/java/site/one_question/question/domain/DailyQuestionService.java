package site.one_question.question.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.question.domain.exception.DailyQuestionNotFoundException;
import site.one_question.question.domain.exception.QuestionNotFoundException;

@Service
@RequiredArgsConstructor
public class DailyQuestionService {

    private final QuestionRepository questionRepository;
    private final DailyQuestionRepository dailyQuestionRepository;

    public Optional<DailyQuestion> findByMemberIdAndDate(Long memberId, LocalDate date) {
        return dailyQuestionRepository.findByMemberIdAndDate(memberId, date);
    }

    public DailyQuestion findByMemberIdAndDateOrThrow(Long memberId, LocalDate date) {
        return dailyQuestionRepository.findByMemberIdAndDate(memberId, date)
            .orElseThrow(DailyQuestionNotFoundException::new);
    }

    /**
     * 사이클 내에서 아직 제공되지 않은 질문 중 랜덤으로 하나를 선택한다.
     * 모든 질문이 이미 제공된 경우, 전체 질문에서 중복을 허용하여 선택한다.
     *
     * @param cycle 질문 사이클 도메인
     * @return 랜덤으로 선택된 질문
     * @throws QuestionNotFoundException 활성화된 질문이 없는 경우
     */
    public Question selectRandomQuestion(QuestionCycle cycle) {
        List<Long> servedQuestionIds = dailyQuestionRepository.findQuestionIdsByCycleId(cycle.getId());

        List<Question> candidates = questionRepository.findAllByStatusAndIdNotIn(
            QuestionStatus.ACTIVE, servedQuestionIds);

        if (candidates.isEmpty()) { // 이미 모든 질문이 한 번씩 제공 됐다면, 질문 중복 허용
            candidates = questionRepository.findAllByStatus(QuestionStatus.ACTIVE);
        }

        if (candidates.isEmpty()) {
            throw new QuestionNotFoundException();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(randomIndex);
    }

    public DailyQuestion save(DailyQuestion dailyQuestion) {
        return dailyQuestionRepository.save(dailyQuestion);
    }

    /**
     * 특정 질문을 제외하고 사이클 내에서 아직 제공되지 않은 질문 중 랜덤으로 하나를 선택한다.
     * 질문 재할당(reload) 시 사용되며, 현재 질문과 동일한 질문이 다시 선택되는 것을 방지한다.
     * 모든 질문이 이미 제공된 경우, 현재 질문만 제외하고 전체에서 선택한다.
     *
     * @param cycle 질문 사이클 도메인
     * @param excludeQuestion 제외할 질문 도메인 (현재 할당된 질문)
     * @return 랜덤으로 선택된 질문
     * @throws QuestionNotFoundException 선택 가능한 질문이 없는 경우
     */
    public Question selectRandomQuestionExcluding(QuestionCycle cycle, Question excludeQuestion) {
        List<Long> servedQuestionIds = dailyQuestionRepository.findQuestionIdsByCycleId(cycle.getId());
        servedQuestionIds.add(excludeQuestion.getId());

        List<Question> candidates = questionRepository.findAllByStatusAndIdNotIn(
            QuestionStatus.ACTIVE, servedQuestionIds);

        if (candidates.isEmpty()) {
            candidates = questionRepository.findAllByStatusAndIdNot(QuestionStatus.ACTIVE, excludeQuestion.getId());
        }

        if (candidates.isEmpty()) {
            throw new QuestionNotFoundException();
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(randomIndex);
    }
}
