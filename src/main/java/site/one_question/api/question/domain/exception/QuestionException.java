package site.one_question.api.question.domain.exception;

import java.util.Map;
import site.one_question.exception.BaseException;
import site.one_question.exception.spec.ExceptionSpec;

public abstract class QuestionException extends BaseException {

    protected QuestionException(ExceptionSpec spec) {
        super(spec);
    }

    protected QuestionException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }

    protected QuestionException(ExceptionSpec spec, Map<String, Object> context, String customLogMessage) {
        super(spec, context, customLogMessage);
    }
}
