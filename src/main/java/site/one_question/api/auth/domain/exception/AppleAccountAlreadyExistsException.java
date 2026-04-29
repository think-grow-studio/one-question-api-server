package site.one_question.api.auth.domain.exception;

import java.util.Map;

public class AppleAccountAlreadyExistsException extends AuthException {

    public AppleAccountAlreadyExistsException() {
        super(AuthExceptionSpec.APPLE_ACCOUNT_ALREADY_EXISTS);
    }

    public AppleAccountAlreadyExistsException(String reason) {
        super(AuthExceptionSpec.APPLE_ACCOUNT_ALREADY_EXISTS, Map.of("reason", reason));
    }
}
