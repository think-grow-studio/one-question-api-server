package site.one_question.auth.domain.exception;

import java.util.Map;

public class AppleTokenVerificationException extends AuthException {

    public AppleTokenVerificationException() {
        super(AuthExceptionSpec.APPLE_VERIFICATION_FAILED);
    }

    public AppleTokenVerificationException(String reason) {
        super(AuthExceptionSpec.APPLE_VERIFICATION_FAILED, Map.of("reason", reason));
    }

    public AppleTokenVerificationException(Throwable cause) {
        super(AuthExceptionSpec.APPLE_VERIFICATION_FAILED, Map.of(), cause);
    }
}
