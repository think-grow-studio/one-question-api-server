package site.one_question.auth.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.global.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum AuthExceptionSpec implements ExceptionSpec {
    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AUTH-001",
            "유효하지 않은 토큰",
            "error.auth.invalid-token"
    ),
    REFRESH_TOKEN_NOT_FOUND(
            HttpStatus.UNAUTHORIZED,
            "AUTH-002",
            "리프레시 토큰을 찾을 수 없음",
            "error.auth.refresh-token-not-found"
    ),
    REFRESH_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-003",
            "Refresh 토큰 만료",
            "error.auth.refresh-token-expired"
    ),
    GOOGLE_VERIFICATION_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-004",
            "Google 토큰 검증 실패",
            "error.auth.google-verification-failed"
    ),
    APPLE_VERIFICATION_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-005",
            "Apple 토큰 검증 실패",
            "error.auth.apple-verification-failed"
    ),
    REFRESH_TOKEN_MISMATCH(
            HttpStatus.UNAUTHORIZED,
            "AUTH-006",
            "Refresh 토큰 불일치",
            "error.auth.refresh-token-mismatch"
    ),
    AUTHENTICATION_FAIL(
            HttpStatus.UNAUTHORIZED,
            "AUTH-007",
            "인증되지 않은 요청",
            "error.auth.authentication-fail"
    ),
    UNKNOWN_AUTHENTICATION_FAILURE(
            HttpStatus.UNAUTHORIZED,
            "AUTH-008",
            "알 수 없는 이유로 인증 실패",
            "error.auth.unknown-authentication-failure"
    ),
    ACCESS_TOKEN_EXCEPTION(
            HttpStatus.UNAUTHORIZED,
            "AUTH-009",
            "JWT(accessToken) 인증 실패",
            "error.auth.access-token-exception"
    ),
    ACCESS_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-010",
            "JWT(accessToken) 만료",
            "error.auth.access-token-expired"
    );


    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
