package site.one_question.auth.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 인증 응답")
public record AuthResponse(
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String refreshToken,

        @Schema(description = "신규 회원 여부", example = "true")
        boolean isNewMember
) {
}
