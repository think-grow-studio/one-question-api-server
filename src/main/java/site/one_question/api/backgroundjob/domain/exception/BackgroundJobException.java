package site.one_question.api.backgroundjob.domain.exception;

import java.util.Map;
import site.one_question.exception.BaseException;
import site.one_question.exception.spec.ExceptionSpec;

public abstract class BackgroundJobException extends BaseException {

    protected BackgroundJobException(ExceptionSpec spec) {
        super(spec);
    }

    protected BackgroundJobException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }
}
