package site.one_question.auth.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Apple OAuth 인증 요청")
public record AppleAuthRequest(
        @Schema(description = "Apple Identity 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        @NotBlank
        String identityToken,

        @Schema(description = "사용자 이름 (첫 로그인 시에만 제공)", example = "홍길동")
        String name
) {
}
