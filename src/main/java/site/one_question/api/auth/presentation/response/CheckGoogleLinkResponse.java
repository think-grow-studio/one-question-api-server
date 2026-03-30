package site.one_question.api.auth.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Google 계정 연결 확인 응답")
public record CheckGoogleLinkResponse(
        @Schema(description = "기존 Google 계정 존재 여부", example = "false")
        boolean exists
) {
}
