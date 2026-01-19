package site.one_question.question.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "답변 작성 요청")
public record CreateAnswerRequest(
        @Schema(description = "답변 내용", example = "새로운 시작의 날")
        String answer
) {
}
