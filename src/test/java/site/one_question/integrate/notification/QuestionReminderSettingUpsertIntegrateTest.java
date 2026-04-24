package site.one_question.integrate.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.presentation.request.SetQuestionReminderSettingRequest;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("알림 설정 저장 API 통합 테스트")
class QuestionReminderSettingUpsertIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Test
    @DisplayName("알림 설정 최초 저장 성공")
    void upsert_creates_new_setting() throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                "08:00", "Asia/Seoul", true
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmTime").value("08:00"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(questionReminderSettingRepository.findByMember(member))
                .as("알림 설정이 DB에 저장되어야 함")
                .isPresent()
                .hasValueSatisfying(s -> {
                    assertThat(s.getAlarmTime()).isEqualTo("08:00");
                    assertThat(s.getTimezone()).isEqualTo("Asia/Seoul");
                    assertThat(s.isEnabled()).isTrue();
                });
    }

    @Test
    @DisplayName("알림 설정 재저장 시 기존 설정이 업데이트된다")
    void upsert_updates_existing_setting() throws Exception {
        put_setting("08:00", "Asia/Seoul", true);

        SetQuestionReminderSettingRequest second = new SetQuestionReminderSettingRequest(
                "21:30", "America/New_York", false
        );
        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmTime").value("21:30"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(questionReminderSettingRepository.findAll())
                .as("중복 저장 없이 1개만 존재해야 함")
                .hasSize(1);

        assertThat(questionReminderSettingRepository.findByMember(member))
                .hasValueSatisfying(s -> {
                    assertThat(s.getAlarmTime()).isEqualTo("21:30");
                    assertThat(s.getTimezone()).isEqualTo("America/New_York");
                    assertThat(s.isEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("enabled=false로 신규 생성 시 DB에 비활성 상태로 저장된다")
    void upsert_creates_disabled_setting() throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                "08:00", "Asia/Seoul", false
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(questionReminderSettingRepository.findByMember(member))
                .as("enabled=false로 신규 생성 요청 시 DB에도 false로 저장되어야 함")
                .isPresent()
                .hasValueSatisfying(s -> assertThat(s.isEnabled()).isFalse());
    }

    @Test
    @DisplayName("enabled 토글 - true → false → true 각각 반영된다")
    void upsert_toggles_enabled() throws Exception {
        put_setting("08:00", "Asia/Seoul", true);

        put_setting("08:00", "Asia/Seoul", false);
        assertThat(questionReminderSettingRepository.findByMember(member))
                .hasValueSatisfying(s -> assertThat(s.isEnabled()).isFalse());

        put_setting("08:00", "Asia/Seoul", true);
        assertThat(questionReminderSettingRepository.findByMember(member))
                .hasValueSatisfying(s -> assertThat(s.isEnabled()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "23:59"})
    @DisplayName("경계값 alarmTime(00:00, 23:59)은 정상 저장된다")
    void upsert_accepts_boundary_alarm_times(String alarmTime) throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                alarmTime, "Asia/Seoul", true
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmTime").value(alarmTime));
    }

    @Test
    @DisplayName("잘못된 alarmTime 형식으로 저장 시 400 반환")
    void upsert_returns_400_for_invalid_alarm_time() throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                "25:00", "Asia/Seoul", true
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:60", "8:00", "25:00", "invalid", ""})
    @DisplayName("잘못된 alarmTime 포맷들은 모두 400을 반환한다")
    void upsert_returns_400_for_various_invalid_alarm_time_formats(String invalidAlarmTime) throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                invalidAlarmTime, "Asia/Seoul", true
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 timezone 으로 저장 시 400 반환")
    void upsert_returns_400_for_blank_timezone() throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                "08:00", "", true
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 알림 설정 저장 시 401 반환")
    void upsert_returns_401_without_auth() throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                "08:00", "Asia/Seoul", true
        );

        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ========== 헬퍼 ==========

    private void put_setting(String alarmTime, String timezone, boolean enabled) throws Exception {
        SetQuestionReminderSettingRequest request = new SetQuestionReminderSettingRequest(
                alarmTime, timezone, enabled
        );
        mockMvc.perform(put(NOTIFICATION_SETTING_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
