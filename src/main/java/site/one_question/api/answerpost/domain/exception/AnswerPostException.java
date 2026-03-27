package site.one_question.api.answerpost.domain.exception;

import java.util.Map;
import site.one_question.global.exception.BaseException;
import site.one_question.global.exception.spec.ExceptionSpec;

public abstract class AnswerPostException extends BaseException {

    protected AnswerPostException(ExceptionSpec spec) {
        super(spec);
    }

    protected AnswerPostException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }

    protected AnswerPostException(ExceptionSpec spec, Map<String, Object> context, String customLogMessage) {
        super(spec, context, customLogMessage);
    }
}
