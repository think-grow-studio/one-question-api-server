package site.one_question.auth.domain.exception;

import java.util.Map;
import site.one_question.global.exception.BaseException;
import site.one_question.global.exception.spec.ExceptionSpec;

public abstract class AuthException extends BaseException {

    protected AuthException(ExceptionSpec spec) {
        super(spec);
    }

    protected AuthException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }

    protected AuthException(ExceptionSpec spec, Map<String, Object> context, String customLogMessage) {
        super(spec, context, customLogMessage);
    }

    protected AuthException(ExceptionSpec spec, Map<String, Object> context, Throwable cause) {
        super(spec, context, cause);
    }
}
