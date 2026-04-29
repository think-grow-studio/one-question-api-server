package site.one_question.api.auth.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Apple 계정 연결 확인 응답")
public record CheckAppleLinkResponse(
        @Schema(description = "기존 Apple 계정 존재 여부", example = "false")
        boolean exists
) {
}
