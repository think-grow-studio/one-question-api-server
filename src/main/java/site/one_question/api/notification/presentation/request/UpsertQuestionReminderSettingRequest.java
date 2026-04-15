package site.one_question.api.notification.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "알림 설정 요청")
public record UpsertQuestionReminderSettingRequest(
        @Schema(description = "알람 시간 (HH:mm)", example = "08:00")
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "알람 시간은 HH:mm 형식이어야 합니다.")
        String alarmTime,

        @Schema(description = "타임존", example = "Asia/Seoul")
        @NotBlank
        String timezone,

        @Schema(description = "알림 활성화 여부", example = "true")
        boolean enabled
) {
}
