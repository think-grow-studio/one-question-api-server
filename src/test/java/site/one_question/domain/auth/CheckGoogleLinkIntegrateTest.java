package site.one_question.domain.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.auth.domain.exception.GoogleTokenVerificationException;
import site.one_question.api.auth.presentation.request.CheckGoogleLinkRequest;
import site.one_question.api.member.domain.Member;
import site.one_question.test_config.IntegrateTest;

import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;

@DisplayName("Google 계정 연결 확인 API 통합 테스트")
class CheckGoogleLinkIntegrateTest extends IntegrateTest {

    private final String LINK_CHECK_URL = AUTH_API + "/google/link/check";

    private Member anonymousMember;
    private String token;

    @BeforeEach
    void setUp() {
        anonymousMember = testMemberUtils.createSave_Anonymous();
        token = testAuthUtils.createBearerToken(anonymousMember);
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("기존 Google 계정이 존재하면 exists가 true이다")
        void returns_true_when_google_account_exists() throws Exception {
            // given
            Member googleMember = testMemberUtils.createSave();

            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setSubject(googleMember.getProviderId());
            payload.setEmail(googleMember.getEmail());

            given(googleTokenVerifier.verify(anyString())).willReturn(payload);

            String requestBody = objectMapper.writeValueAsString(
                    new CheckGoogleLinkRequest("fake-google-id-token")
            );

            // when & then
            mockMvc.perform(post(LINK_CHECK_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(ACCEPT_LANGUAGE, "ko-KR")
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("기존 Google 계정이 없으면 exists가 false이다")
        void returns_false_when_google_account_not_exists() throws Exception {
            // given
            GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
            payload.setSubject("new-google-provider-id");
            payload.setEmail("new@gmail.com");

            given(googleTokenVerifier.verify(anyString())).willReturn(payload);

            String requestBody = objectMapper.writeValueAsString(
                    new CheckGoogleLinkRequest("fake-google-id-token")
            );

            // when & then
            mockMvc.perform(post(LINK_CHECK_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(ACCEPT_LANGUAGE, "ko-KR")
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("Google 토큰 검증 실패 시 401을 반환한다")
        void google_token_verification_failure_returns_401() throws Exception {
            // given
            given(googleTokenVerifier.verify(anyString()))
                    .willThrow(new GoogleTokenVerificationException("Invalid token"));

            String requestBody = objectMapper.writeValueAsString(
                    new CheckGoogleLinkRequest("invalid-token")
            );

            // when & then
            mockMvc.perform(post(LINK_CHECK_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(ACCEPT_LANGUAGE, "ko-KR")
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH-004"));
        }
    }
}
