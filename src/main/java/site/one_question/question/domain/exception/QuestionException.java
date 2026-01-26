package site.one_question.question.domain.exception;

import java.util.Map;
import site.one_question.global.exception.BaseException;
import site.one_question.global.exception.spec.ExceptionSpec;

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
