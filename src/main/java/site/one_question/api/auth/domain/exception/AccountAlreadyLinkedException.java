package site.one_question.api.auth.domain.exception;

import java.util.Map;

public class AccountAlreadyLinkedException extends AuthException {

    public AccountAlreadyLinkedException() {
        super(AuthExceptionSpec.ACCOUNT_ALREADY_LINKED);
    }

    public AccountAlreadyLinkedException(String reason) {
        super(AuthExceptionSpec.ACCOUNT_ALREADY_LINKED, Map.of("reason", reason));
    }
}
