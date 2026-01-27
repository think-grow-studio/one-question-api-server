package site.one_question.auth.domain.exception;

import java.util.Map;

public class GoogleTokenVerificationException extends AuthException {

    public GoogleTokenVerificationException() {
        super(AuthExceptionSpec.GOOGLE_VERIFICATION_FAILED);
    }

    public GoogleTokenVerificationException(String reason) {
        super(AuthExceptionSpec.GOOGLE_VERIFICATION_FAILED, Map.of("reason", reason));
    }

    public GoogleTokenVerificationException(Throwable cause) {
        super(AuthExceptionSpec.GOOGLE_VERIFICATION_FAILED, Map.of(), cause);
    }
}
