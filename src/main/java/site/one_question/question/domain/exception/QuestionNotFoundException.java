package site.one_question.question.domain.exception;

import java.util.Map;

public class QuestionNotFoundException extends QuestionException {

    public QuestionNotFoundException() {
        super(QuestionExceptionSpec.QUESTION_NOT_FOUND);
    }

    public QuestionNotFoundException(Long questionId) {
        super(QuestionExceptionSpec.QUESTION_NOT_FOUND, Map.of("questionId", questionId));
    }
}
