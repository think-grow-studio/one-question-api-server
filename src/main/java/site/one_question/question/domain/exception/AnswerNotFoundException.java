package site.one_question.question.domain.exception;

import java.util.Map;

public class AnswerNotFoundException extends QuestionException {

    public AnswerNotFoundException(Long answerId) {
        super(QuestionExceptionSpec.ANSWER_NOT_FOUND, Map.of("answerId", answerId));
    }
}
