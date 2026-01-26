package site.one_question.question.domain.exception;

import java.util.Map;

public class AnswerAlreadyExistsException extends QuestionException {

    public AnswerAlreadyExistsException(Long dailyQuestionId) {
        super(QuestionExceptionSpec.ANSWER_ALREADY_EXISTS, Map.of("dailyQuestionId", dailyQuestionId));
    }
}
