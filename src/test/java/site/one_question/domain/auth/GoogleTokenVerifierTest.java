package site.one_question.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.api.auth.domain.exception.AuthExceptionSpec;
import site.one_question.api.auth.domain.exception.GoogleTokenVerificationException;
import site.one_question.api.auth.infrastructure.oauth.GoogleTokenVerifier;

@DisplayName("Google 토큰 검증 오류 처리")
class GoogleTokenVerifierTest {

    private final GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
    private final GoogleTokenVerifier googleTokenVerifier = new GoogleTokenVerifier(verifier);

    @Test
    @DisplayName("메시지가 없는 검증 예외도 인증 오류로 변환하고 원인을 보존한다")
    void verify_when_cause_has_null_message_then_preserves_auth_error_and_cause() throws Exception {
        IllegalArgumentException cause = new IllegalArgumentException();
        given(verifier.verify("invalid-token")).willThrow(cause);

        assertThatThrownBy(() -> googleTokenVerifier.verify("invalid-token"))
                .isInstanceOfSatisfying(GoogleTokenVerificationException.class, exception -> {
                    assertThat(exception.getSpec()).isEqualTo(AuthExceptionSpec.GOOGLE_VERIFICATION_FAILED);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    @DisplayName("검증 결과가 null이면 잘못된 토큰 오류의 이유를 보존한다")
    void verify_when_token_is_rejected_then_preserves_reason() throws Exception {
        given(verifier.verify("rejected-token")).willReturn(null);

        assertThatThrownBy(() -> googleTokenVerifier.verify("rejected-token"))
                .isInstanceOfSatisfying(GoogleTokenVerificationException.class, exception -> {
                    assertThat(exception.getSpec()).isEqualTo(AuthExceptionSpec.GOOGLE_VERIFICATION_FAILED);
                    assertThat(exception.getContext()).containsEntry("reason", "Invalid Google ID Token");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    @DisplayName("검증 성공 시 원래 payload를 반환한다")
    void verify_when_token_is_valid_then_returns_payload() throws Exception {
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        given(verifier.verify("valid-token")).willReturn(token);
        given(token.getPayload()).willReturn(payload);

        assertThat(googleTokenVerifier.verify("valid-token")).isSameAs(payload);
    }

    @Test
    @DisplayName("검증 실패 이유가 null이어도 예외 생성에서 NPE가 발생하지 않는다")
    void create_exception_when_reason_is_null_then_retains_auth_error() {
        GoogleTokenVerificationException exception = new GoogleTokenVerificationException((String) null);

        assertThat(exception.getSpec()).isEqualTo(AuthExceptionSpec.GOOGLE_VERIFICATION_FAILED);
        assertThat(exception.getContext()).isEmpty();
    }
}
