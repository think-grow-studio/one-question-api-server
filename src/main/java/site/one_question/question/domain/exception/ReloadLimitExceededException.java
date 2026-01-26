package site.one_question.question.domain.exception;

import java.util.Map;

public class ReloadLimitExceededException extends QuestionException {

    private final int maxChangeCount;

    public ReloadLimitExceededException(int maxChangeCount) {
        super(QuestionExceptionSpec.RELOAD_LIMIT_EXCEEDED, Map.of("maxChangeCount", maxChangeCount));
        this.maxChangeCount = maxChangeCount;
    }

    @Override
    public Object[] getMessageArgs() {
        return new Object[]{maxChangeCount};
    }
}
