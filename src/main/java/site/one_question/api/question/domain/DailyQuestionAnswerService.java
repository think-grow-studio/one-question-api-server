package site.one_question.api.question.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.question.domain.exception.AnswerNotFoundException;

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

    public DailyQuestionAnswer findById(Long id) {
        return answerRepository.findById(id)
            .orElseThrow(() -> new AnswerNotFoundException(id));
    }

    public void deleteByMemberId(Long memberId) {
        answerRepository.deleteByMemberId(memberId);
    }
}
