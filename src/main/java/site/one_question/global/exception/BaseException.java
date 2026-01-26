package site.one_question.global.exception;

import java.util.Map;
import java.util.StringJoiner;
import site.one_question.global.exception.spec.ExceptionSpec;

public abstract class BaseException extends RuntimeException {
    private final ExceptionSpec spec;
    private final Map<String, Object> context;
    private final String customLogMessage;

    protected BaseException(ExceptionSpec spec) {
        super(spec.getLogMessage());
        this.spec = spec;
        this.context = Map.of();
        this.customLogMessage = null;
    }

    protected BaseException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec.getLogMessage());
        this.spec = spec;
        this.context = Map.copyOf(context);
        this.customLogMessage = null;
    }

    protected BaseException(ExceptionSpec spec, Map<String, Object> context, String customLogMessage) {
        super(customLogMessage != null ? customLogMessage : spec.getLogMessage());
        this.spec = spec;
        this.context = Map.copyOf(context);
        this.customLogMessage = customLogMessage;
    }

    protected BaseException(ExceptionSpec spec, Map<String, Object> context, Throwable cause) {
        super(spec.getLogMessage(), cause);
        this.spec = spec;
        this.context = Map.copyOf(context);
        this.customLogMessage = null;
    }

    public ExceptionSpec getSpec() {
        return spec;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public String getLogMessage() {
        String baseMessage = customLogMessage != null ? customLogMessage : spec.getLogMessage();
        if (context.isEmpty()) {
            return String.format("[%s] %s", spec.getCode(), baseMessage);
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        context.forEach((k, v) -> joiner.add(k + "=" + v));
        return String.format("[%s] %s %s", spec.getCode(), baseMessage, joiner);
    }

    public String getClientMessageKey() {
        return spec.getClientMessageKey();
    }

    public Object[] getMessageArgs() {
        return new Object[0];
    }
}
