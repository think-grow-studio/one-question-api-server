package site.one_question.api.answerpost.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "답변 공개 게시 요청")
public record PublishAnswerPostRequest(
        @Schema(description = "공개할 답변 ID", example = "156")
        Long questionAnswerId
) {}
