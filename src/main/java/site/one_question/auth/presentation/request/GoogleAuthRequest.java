package site.one_question.auth.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google OAuth 인증 요청")
public record GoogleAuthRequest(
        @Schema(description = "Google ID 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        @NotBlank
        String idToken,

        @Schema(description = "사용자 이메일", example = "user@gmail.com")
        String email,

        @Schema(description = "사용자 이름", example = "홍길동")
        String name
) {
}
