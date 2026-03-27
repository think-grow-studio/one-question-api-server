package site.one_question.api.answerpost.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "공개 답변 피드 응답")
public record AnswerPostFeedResponse(
        @Schema(description = "피드 아이템 목록")
        List<AnswerPostFeedItemDto> items,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 커서 (다음 요청 시 cursor 파라미터로 사용)")
        Instant nextCursor
) {}
