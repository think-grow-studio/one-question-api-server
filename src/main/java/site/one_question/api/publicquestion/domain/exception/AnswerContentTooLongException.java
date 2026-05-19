package site.one_question.api.publicquestion.domain.exception;

import java.util.Map;

public class AnswerContentTooLongException extends PublicQuestionException {

    private final int maxLength;

    public AnswerContentTooLongException(int maxLength) {
        super(PublicQuestionExceptionSpec.ANSWER_CONTENT_TOO_LONG, Map.of("maxLength", maxLength));
        this.maxLength = maxLength;
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[]{maxLength};
    }
}
