package site.one_question.question.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.question.domain.exception.QuestionNotFoundException;

@Service
@RequiredArgsConstructor
public class DailyQuestionService {

    private final QuestionRepository questionRepository;
    private final DailyQuestionRepository dailyQuestionRepository;

    public Optional<DailyQuestion> findByMemberIdAndDate(Long memberId, LocalDate date) {
        return dailyQuestionRepository.findByMemberIdAndDate(memberId, date);
    }

    public Question selectRandomQuestion(Long cycleId) {
        List<Long> servedQuestionIds = dailyQuestionRepository.findQuestionIdsByCycleId(cycleId);

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
}
