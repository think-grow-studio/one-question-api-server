package site.one_question.auth.domain.exception;

import java.util.Map;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException() {
        super(AuthExceptionSpec.INVALID_TOKEN);
    }

    public InvalidTokenException(String reason) {
        super(AuthExceptionSpec.INVALID_TOKEN, Map.of("reason", reason));
    }
}
