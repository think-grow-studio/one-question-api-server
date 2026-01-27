package site.one_question.auth.domain.exception;

import java.util.Map;

public class RefreshTokenMismatchException extends AuthException {

    public RefreshTokenMismatchException(Long memberId) {
        super(AuthExceptionSpec.REFRESH_TOKEN_MISMATCH, Map.of("memberId", memberId));
    }
}
