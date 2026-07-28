package site.one_question.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExceptionResponse {
    private final String requestId;
    private final int status;
    private final String code;
    private final String message;

    public static ExceptionResponse of(String requestId, int status, String code, String message) {
        return ExceptionResponse.builder()
                .requestId(requestId)
                .status(status)
                .code(code)
                .message(message)
                .build();
    }
}
