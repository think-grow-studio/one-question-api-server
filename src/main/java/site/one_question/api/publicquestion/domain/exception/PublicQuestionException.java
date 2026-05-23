package site.one_question.api.publicquestion.domain.exception;

import java.util.Map;
import site.one_question.exception.BaseException;
import site.one_question.exception.spec.ExceptionSpec;

public abstract class PublicQuestionException extends BaseException {

    protected PublicQuestionException(ExceptionSpec spec) {
        super(spec);
    }

    protected PublicQuestionException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }

    protected PublicQuestionException(ExceptionSpec spec, Map<String, Object> context, String customLogMessage) {
        super(spec, context, customLogMessage);
    }
}
