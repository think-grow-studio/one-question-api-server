package site.one_question.api.auth.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Apple 계정 연결 확인 요청")
public record CheckAppleLinkRequest(
        @Schema(description = "Apple Identity 토큰", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        @NotBlank
        String identityToken,

        @Schema(description = "클라이언트에서 생성한 raw nonce (replay 방지). signInAsync 호출 시 nonce 옵션에 SHA256(rawNonce) 16진수 lowercase 문자열을 넘기고, 서버에는 rawNonce를 그대로 전송",
                example = "f37b0c8a-...-b1c2d3e4")
        @NotBlank
        String rawNonce
) {
}
