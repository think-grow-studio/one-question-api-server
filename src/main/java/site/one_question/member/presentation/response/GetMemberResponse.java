package site.one_question.member.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "회원 정보 조회 응답")
public record GetMemberResponse(
        @Schema(description = "회원 ID", example = "1")
        Long id,

        @Schema(description = "이메일", example = "user@gmail.com")
        String email,

        @Schema(description = "이름", example = "홍길동")
        String fullName,

        @Schema(description = "로그인 제공자", example = "GOOGLE")
        String provider,

        @Schema(description = "사용 언어", example = "ko")
        String locale,

        @Schema(description = "권한 (추후 변경될 수 있음)", example = "FREE", allowableValues = {"FREE", "PREMIUM"})
        String permission,

        @Schema(description = "상태", example = "ACTIVE", allowableValues = {"ACTIVE", "BLOCKED", "WITHDRAWAL_REQUESTED"})
        String status,

        @Schema(description = "가입일시", example = "2024-01-01T10:00:00")
        LocalDateTime joinedAt
) {
}
