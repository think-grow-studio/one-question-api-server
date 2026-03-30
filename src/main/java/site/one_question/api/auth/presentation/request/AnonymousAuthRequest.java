package site.one_question.api.auth.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "익명 로그인 요청")
public record AnonymousAuthRequest(
        @Schema(description = "Firebase ID 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        @NotBlank
        String idToken
) {
}
