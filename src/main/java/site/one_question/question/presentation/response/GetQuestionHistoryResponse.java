package site.one_question.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "질문 히스토리 응답")
public record GetQuestionHistoryResponse(
        @Schema(description = "질문 히스토리 목록")
        List<QuestionHistoryItemDto> histories,

        @Schema(description = "이전 날짜가 더 있는지 여부", example = "true")
        boolean hasPrevious,

        @Schema(description = "다음 날짜가 더 있는지 여부", example = "true")
        boolean hasNext,

        @Schema(description = "조회된 시작 날짜", example = "2024-01-10")
        LocalDate startDate,

        @Schema(description = "조회된 끝 날짜", example = "2024-01-15")
        LocalDate endDate
) {
}
