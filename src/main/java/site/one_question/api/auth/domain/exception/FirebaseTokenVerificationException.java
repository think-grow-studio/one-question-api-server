package site.one_question.api.auth.domain.exception;

import java.util.Map;

public class FirebaseTokenVerificationException extends AuthException {

    public FirebaseTokenVerificationException() {
        super(AuthExceptionSpec.FIREBASE_VERIFICATION_FAILED);
    }

    public FirebaseTokenVerificationException(String reason) {
        super(AuthExceptionSpec.FIREBASE_VERIFICATION_FAILED, Map.of("reason", reason));
    }

    public FirebaseTokenVerificationException(Throwable cause) {
        super(AuthExceptionSpec.FIREBASE_VERIFICATION_FAILED, Map.of(), cause);
    }
}
