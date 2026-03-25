package site.one_question.api.auth.domain.exception;

import java.util.Map;

public class RefreshTokenNotFoundException extends AuthException {

    public RefreshTokenNotFoundException(Long memberId) {
        super(AuthExceptionSpec.REFRESH_TOKEN_NOT_FOUND, Map.of("memberId", memberId));
    }
}
