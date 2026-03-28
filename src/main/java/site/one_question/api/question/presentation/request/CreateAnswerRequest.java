package site.one_question.api.question.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "답변 작성 요청")
public record CreateAnswerRequest(
        @Schema(description = "답변 내용", example = "새로운 시작의 날")
        String answer,

        @Schema(description = "답변을 공개 게시할지 여부 (기본값: false)", example = "false")
        Boolean publish
) {
    public boolean shouldPublish() {
        return Boolean.TRUE.equals(publish);
    }
}
