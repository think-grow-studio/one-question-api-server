package site.one_question.api.notification.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import site.one_question.api.notification.domain.QuestionReminderSetting;

@Schema(description = "알림 설정 응답")
public record QuestionReminderSettingResponse(
        @Schema(description = "알람 시간 (HH:mm)", example = "08:00")
        String alarmTime,

        @Schema(description = "알림 활성화 여부", example = "true")
        boolean enabled
) {
    public static QuestionReminderSettingResponse from(QuestionReminderSetting setting) {
        return new QuestionReminderSettingResponse(
                setting.getAlarmTime(),
                setting.isEnabled()
        );
    }
}
