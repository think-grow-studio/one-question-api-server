package site.one_question.api.auth.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google 계정 연결 요청")
public record LinkToGoogleRequest(
        @Schema(description = "Google ID 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        @NotBlank
        String idToken,

        @Schema(description = "사용자 이름 (선택)", example = "홍길동")
        String name
) {
}
