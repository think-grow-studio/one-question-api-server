package site.one_question.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘의 질문 응답")
public record ServeDailyQuestionResponse(
        @Schema(description = "질문 ID", example = "43")
        Long dailyQuestionId,

        @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String content,

        @Schema(description = "질문 보충 설명", example = "ex) 폭풍 전야", nullable = true)
        String description,

        @Schema(description = "질문 1년 사이클 횟수", example = "1")
        Long questionCycle,

        @Schema(description = "질문 변경 횟수", example = "2")
        Long changeCount
) {
}
