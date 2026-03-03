package site.one_question.question.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.question.domain.exception.AnswerNotFoundException;

@Service
@RequiredArgsConstructor
public class DailyQuestionAnswerService {

    private final DailyQuestionAnswerRepository answerRepository;

    public boolean hasAnswer(DailyQuestion dailyQuestion) {
        return answerRepository.existsByDailyQuestionId(dailyQuestion);
    }

    public DailyQuestionAnswer save(DailyQuestionAnswer answer) {
        return answerRepository.save(answer);
    }

    public Optional<DailyQuestionAnswer> findByDailyQuestion(DailyQuestion dailyQuestion) {
        return answerRepository.findByDailyQuestionId(dailyQuestion);
    }

    public DailyQuestionAnswer findByDailyQuestionOrThrow(DailyQuestion dailyQuestion) {
        return findByDailyQuestion(dailyQuestion)
                .orElseThrow(() -> new AnswerNotFoundException(dailyQuestion.getId()));
    }

    public void deleteByMemberId(Long memberId) {
        answerRepository.deleteByMemberId(memberId);
    }
}
