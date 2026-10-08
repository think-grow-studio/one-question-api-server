package site.one_question.integrate.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import site.one_question.api.auth.infrastructure.oauth.GoogleTokenVerifier;
import site.one_question.common.HttpHeaderConstant;
import site.one_question.config.OAuthConfig;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("Google 로그인 검증 실패 API 통합 테스트")
class GoogleAuthFailureIntegrateTest extends IntegrateTest {

    @Test
    @DisplayName("숫자 사용자 ID를 토큰으로 보내면 500 대신 AUTH-004와 401을 반환한다")
    void google_login_when_token_is_numeric_then_returns_auth_error() throws Exception {
        // 숫자 입력은 Google SDK의 JWT 파싱에서 실패하므로 외부 통신이 필요 없다.
        GoogleTokenVerifier realVerifier = new GoogleTokenVerifier(
                new OAuthConfig().googleIdTokenVerifier("test-google-client-id"));
        given(googleTokenVerifier.verify("11688"))
                .willAnswer(invocation -> realVerifier.verify(invocation.getArgument(0)));

        mockMvc.perform(post(AUTH_API + "/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ACCEPT_LANGUAGE, "ko")
                        .header(HttpHeaderConstant.TIMEZONE, "Asia/Seoul")
                        .content("{\"idToken\":\"11688\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-004"))
                .andExpect(jsonPath("$.status").value(401));
    }
}
