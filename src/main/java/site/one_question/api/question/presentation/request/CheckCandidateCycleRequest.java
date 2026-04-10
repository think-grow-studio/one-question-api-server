package site.one_question.api.question.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CheckCandidateCycleRequest(
        @Schema(description = "사이클 중복 여부를 확인할 후보 questionId", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        Long questionId
) {}
