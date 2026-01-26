package site.one_question.question.domain.exception;

import java.util.Map;

public class DailyQuestionNotFoundException extends QuestionException {

    public DailyQuestionNotFoundException() {
        super(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND);
    }

    public DailyQuestionNotFoundException(Long dailyQuestionId) {
        super(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND, Map.of("dailyQuestionId", dailyQuestionId));
    }
}
