package site.one_question.integrate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.domain.QuestionReminderSetting;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("로그아웃 API 통합 테스트")
class AuthLogoutIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";
    private static final String ALARM_TIME = "09:00";

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        testRefreshTokenUtils.createSave_Valid(member, "refresh-token");
    }

    @Test
    @DisplayName("로그아웃 시 리프레시 토큰이 삭제된다")
    void logout_deletesRefreshToken() throws Exception {
        mockMvc.perform(post(AUTH_API + "/logout")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findByMember_Id(member.getId()))
                .as("로그아웃 시 리프레시 토큰이 삭제되어야 함")
                .isEmpty();
    }

    @Test
    @DisplayName("로그아웃 시 FCM 토큰이 삭제된다")
    void logout_deletesFcmToken() throws Exception {
        testFcmTokenUtils.createSave(member, "test-fcm-token");

        mockMvc.perform(post(AUTH_API + "/logout")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        assertThat(fcmTokenRepository.findByMember(member))
                .as("로그아웃 시 FCM 토큰이 삭제되어야 함")
                .isEmpty();
    }

    @Test
    @DisplayName("로그아웃해도 알림 설정은 사용자 선호로 그대로 유지된다")
    void logout_doesNotTouchReminderSetting() throws Exception {
        testQuestionReminderSettingUtils.createSave(member, ALARM_TIME, TIMEZONE);

        mockMvc.perform(post(AUTH_API + "/logout")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        QuestionReminderSetting setting = questionReminderSettingRepository.findByMember(member)
                .orElseThrow(() -> new AssertionError("알림 설정 레코드는 유지되어야 함"));
        assertThat(setting.isEnabled())
                .as("로그아웃은 알림 설정의 활성 상태를 건드리지 않아야 함")
                .isTrue();
        assertThat(setting.getAlarmTime()).isEqualTo(ALARM_TIME);
        assertThat(setting.getTimezone()).isEqualTo(TIMEZONE);
    }

    @Test
    @DisplayName("FCM 토큰과 알림 설정이 없어도 로그아웃은 정상 동작한다")
    void logout_succeeds_whenNoFcmTokenOrReminderSettingExists() throws Exception {
        mockMvc.perform(post(AUTH_API + "/logout")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findByMember_Id(member.getId())).isEmpty();
    }
}
