package site.one_question.member.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 정보 수정 요청")
public record UpdateMemberRequest(
        @Schema(description = "이름", example = "홍길동")
        String fullName,

        @Schema(description = "사용 언어", example = "ko")
        String locale
) {
}
