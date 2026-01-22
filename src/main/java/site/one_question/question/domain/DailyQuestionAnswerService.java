package site.one_question.question.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyQuestionAnswerService {

    private final DailyQuestionAnswerRepository answerRepository;

    public boolean hasAnswer(DailyQuestion dailyQuestion) {
        return answerRepository.existsByDailyQuestionId(dailyQuestion);
    }
}
