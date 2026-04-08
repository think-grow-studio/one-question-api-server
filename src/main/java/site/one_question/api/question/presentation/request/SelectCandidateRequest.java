package site.one_question.api.question.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record SelectCandidateRequest(
        @Schema(description = "선택할 후보 질문의 question_id", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        Long questionId
) {}
