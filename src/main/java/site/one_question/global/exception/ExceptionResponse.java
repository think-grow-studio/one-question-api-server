package site.one_question.global.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExceptionResponse {
    private final String traceId;
    private final int status;
    private final String code;
    private final String message;

    public static ExceptionResponse of(String traceId, int status, String code, String message) {
        return ExceptionResponse.builder()
                .traceId(traceId)
                .status(status)
                .code(code)
                .message(message)
                .build();
    }
}
