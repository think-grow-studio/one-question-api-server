package site.one_question.integrate.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.presentation.request.DeleteFcmTokenRequest;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("FCM 토큰 삭제 API 통합 테스트")
class FcmTokenDeleteIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Test
    @DisplayName("FCM 토큰 삭제 성공 - 204 반환 및 DB에서 제거")
    void delete_removes_fcm_token() throws Exception {
        testFcmTokenUtils.createSave(member, "test-token");

        DeleteFcmTokenRequest request = new DeleteFcmTokenRequest("test-token");

        mockMvc.perform(delete(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findByMember(member))
                .as("토큰이 삭제되어야 함")
                .isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 토큰 삭제 시 204 반환 (silent ignore)")
    void delete_returns_204_for_nonexistent_token() throws Exception {
        DeleteFcmTokenRequest request = new DeleteFcmTokenRequest("non-existent-token");

        mockMvc.perform(delete(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("내 토큰 삭제 시 다른 멤버의 토큰은 영향받지 않는다")
    void delete_does_not_affect_other_member_token() throws Exception {
        testFcmTokenUtils.createSave(member, "my-token");
        Member otherMember = testMemberUtils.createSave();
        testFcmTokenUtils.createSave(otherMember, "other-member-token");

        DeleteFcmTokenRequest request = new DeleteFcmTokenRequest("my-token");

        mockMvc.perform(delete(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findByMember(member))
                .as("내 토큰은 삭제되어야 함")
                .isEmpty();

        assertThat(fcmTokenRepository.findByMember(otherMember))
                .as("다른 멤버의 토큰은 그대로 유지되어야 함")
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getToken()).isEqualTo("other-member-token"));
    }

    @Test
    @DisplayName("빈 문자열 토큰으로 삭제 시 400 반환")
    void delete_returns_400_for_blank_token() throws Exception {
        DeleteFcmTokenRequest request = new DeleteFcmTokenRequest("");

        mockMvc.perform(delete(FCM_TOKEN_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 FCM 토큰 삭제 시 401 반환")
    void delete_returns_401_without_auth() throws Exception {
        DeleteFcmTokenRequest request = new DeleteFcmTokenRequest("test-token");

        mockMvc.perform(delete(FCM_TOKEN_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
