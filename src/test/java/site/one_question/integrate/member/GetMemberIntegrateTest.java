package site.one_question.integrate.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("내 정보 조회 API 통합 테스트")
class GetMemberIntegrateTest extends IntegrateTest {

    private final String GET_ME_URL = MEMBERS_API + "/me";

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        testQuestionCycleUtils.createSave(member);
        token = testAuthUtils.createBearerToken(member);
    }

    @Test
    @DisplayName("알림 설정이 없으면 notificationSetting이 null로 반환된다")
    void getMe_returns_null_notificationSetting_when_not_set() throws Exception {
        mockMvc.perform(get(GET_ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member.getId()))
                .andExpect(jsonPath("$.email").value(member.getEmail()))
                .andExpect(jsonPath("$.fullName").value(member.getFullName()))
                .andExpect(jsonPath("$.notificationSetting").doesNotExist());
    }

    @Test
    @DisplayName("활성화된 알림 설정이 있으면 alarmTime과 enabled=true가 반환된다")
    void getMe_returns_enabled_notificationSetting() throws Exception {
        testQuestionReminderSettingUtils.createSave(member, "08:00", "Asia/Seoul");

        mockMvc.perform(get(GET_ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationSetting").exists())
                .andExpect(jsonPath("$.notificationSetting.alarmTime").value("08:00"))
                .andExpect(jsonPath("$.notificationSetting.enabled").value(true));
    }

    @Test
    @DisplayName("비활성화된 알림 설정이 있으면 enabled=false가 반환된다")
    void getMe_returns_disabled_notificationSetting() throws Exception {
        testQuestionReminderSettingUtils.createSave_Disabled(member, "21:30", "Asia/Seoul");

        mockMvc.perform(get(GET_ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationSetting.alarmTime").value("21:30"))
                .andExpect(jsonPath("$.notificationSetting.enabled").value(false));
    }

    @Test
    @DisplayName("인증 토큰이 없으면 401을 반환한다")
    void getMe_returns_401_without_token() throws Exception {
        mockMvc.perform(get(GET_ME_URL))
                .andExpect(status().isUnauthorized());
    }
}
