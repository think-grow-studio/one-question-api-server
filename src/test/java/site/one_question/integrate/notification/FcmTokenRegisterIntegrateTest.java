package site.one_question.integrate.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.presentation.request.RegisterFcmTokenRequest;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("FCM 토큰 등록 API 통합 테스트")
class FcmTokenRegisterIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Test
    @DisplayName("FCM 토큰 신규 등록 성공 - 204 반환 및 DB 저장")
    void register_saves_fcm_token() throws Exception {
        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest("test-fcm-token-1");

        mockMvc.perform(post(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findByMember(member))
                .as("FCM 토큰이 저장되어야 함")
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getToken()).isEqualTo("test-fcm-token-1"));
    }

    @Test
    @DisplayName("기존 토큰이 있는 상태에서 새 토큰 등록 시 기존 토큰이 교체된다 (member당 1개 보장)")
    void register_replaces_existing_token() throws Exception {
        String newToken = "new-token";
        testFcmTokenUtils.createSave(member, "old-token");

        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest(newToken);

        mockMvc.perform(post(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findAll())
                .as("한 멤버에 하나의 토큰만 존재해야 함")
                .hasSize(1);

        assertThat(fcmTokenRepository.findByMember(member))
                .as("새 토큰으로 교체되어야 함")
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getToken()).isEqualTo(newToken));
    }

    @Test
    @DisplayName("동일 토큰 재등록 시 멱등성 보장 - DB에 1개만 유지")
    void register_same_token_is_idempotent() throws Exception {
        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest("same-token");

        mockMvc.perform(post(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findAll())
                .as("중복 생성 없이 1개만 존재해야 함")
                .hasSize(1);

        assertThat(fcmTokenRepository.findByMember(member))
                .as("기존 토큰이어야 함")
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getToken()).isEqualTo("same-token"));
    }

    @Test
    @DisplayName("빈 문자열 토큰으로 등록 시 400 반환")
    void register_returns_400_for_blank_token() throws Exception {
        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest("");

        mockMvc.perform(post(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("null 토큰으로 등록 시 400 반환")
    void register_returns_400_for_null_token() throws Exception {
        String requestBody = "{\"tokenValue\":null}";

        mockMvc.perform(post(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 FCM 토큰 등록 시 401 반환")
    void register_returns_401_without_auth() throws Exception {
        RegisterFcmTokenRequest request = new RegisterFcmTokenRequest("test-token");

        mockMvc.perform(post(FCM_TOKEN_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
