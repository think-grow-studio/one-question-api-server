package site.one_question.api.question.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record SelectQuestionRequest(
        @Schema(description = "오늘 질문으로 선택할 후보 questionId", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        Long questionId
) {}
