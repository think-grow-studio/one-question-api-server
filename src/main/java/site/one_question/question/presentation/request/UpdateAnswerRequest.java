package site.one_question.question.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "답변 수정 요청")
public record UpdateAnswerRequest(
        @Schema(description = "수정할 답변 내용", example = "오늘은 정말 특별한 하루였다")
        String answer
) {
}
