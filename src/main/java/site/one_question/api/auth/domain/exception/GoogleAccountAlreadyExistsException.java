package site.one_question.api.auth.domain.exception;

import java.util.Map;

public class GoogleAccountAlreadyExistsException extends AuthException {

    public GoogleAccountAlreadyExistsException() {
        super(AuthExceptionSpec.GOOGLE_ACCOUNT_ALREADY_EXISTS);
    }

    public GoogleAccountAlreadyExistsException(String reason) {
        super(AuthExceptionSpec.GOOGLE_ACCOUNT_ALREADY_EXISTS, Map.of("reason", reason));
    }
}
