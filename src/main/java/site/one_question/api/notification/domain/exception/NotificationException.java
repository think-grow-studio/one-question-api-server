package site.one_question.api.notification.domain.exception;

import java.util.Map;
import site.one_question.global.exception.BaseException;
import site.one_question.global.exception.spec.ExceptionSpec;

public abstract class NotificationException extends BaseException {

    protected NotificationException(ExceptionSpec spec) {
        super(spec);
    }

    protected NotificationException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }
}
