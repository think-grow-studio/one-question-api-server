package site.one_question.member.domain.exception;

import java.util.Map;
import site.one_question.global.exception.BaseException;
import site.one_question.global.exception.spec.ExceptionSpec;

public abstract class MemberException extends BaseException {

    protected MemberException(ExceptionSpec spec) {
        super(spec);
    }

    protected MemberException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }

    protected MemberException(ExceptionSpec spec, Map<String, Object> context, String customLogMessage) {
        super(spec, context, customLogMessage);
    }
}
