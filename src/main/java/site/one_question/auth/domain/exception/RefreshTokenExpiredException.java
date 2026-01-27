package site.one_question.auth.domain.exception;

import java.util.Map;

public class RefreshTokenExpiredException extends AuthException {

    public RefreshTokenExpiredException(Long memberId) {
        super(AuthExceptionSpec.REFRESH_TOKEN_EXPIRED, Map.of("memberId", memberId));
    }
}
