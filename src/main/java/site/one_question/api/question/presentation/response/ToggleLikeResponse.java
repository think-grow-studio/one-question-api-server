package site.one_question.api.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좋아요 토글 응답")
public record ToggleLikeResponse(
        @Schema(description = "좋아요 여부", example = "true")
        boolean liked
) {}
