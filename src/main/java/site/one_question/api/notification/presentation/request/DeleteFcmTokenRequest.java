package site.one_question.api.notification.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "FCM 토큰 삭제 요청")
public record DeleteFcmTokenRequest(
        @Schema(description = "삭제할 FCM 디바이스 토큰", example = "eXaMpLeToKeN...")
        @NotBlank
        String tokenValue
) {
}
