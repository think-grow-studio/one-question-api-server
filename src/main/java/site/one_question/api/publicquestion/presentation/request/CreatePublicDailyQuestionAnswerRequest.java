package site.one_question.api.publicquestion.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공개 일일 질문 답변 작성 요청")
public record CreatePublicDailyQuestionAnswerRequest(
        @Schema(description = "답변 내용", example = "오늘 가장 인상 깊었던 일은...")
        String content
) {}
