package site.one_question.integrate.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.presentation.request.SetQuestionReminderSettingRequest;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("알림 설정 조회 API 통합 테스트")
class QuestionReminderSettingGetIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Test
    @DisplayName("알림 설정 조회 성공")
    void get_returns_existing_setting() throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                "09:00", "Asia/Seoul", true
        );
        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmTime").value("09:00"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("알림 설정이 없을 때 조회 시 404 반환")
    void get_returns_404_when_no_setting() throws Exception {
        mockMvc.perform(get(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 없이 알림 설정 조회 시 401 반환")
    void get_returns_401_without_auth() throws Exception {
        mockMvc.perform(get(NOTIFICATION_SETTING_API))
                .andExpect(status().isUnauthorized());
    }
}
