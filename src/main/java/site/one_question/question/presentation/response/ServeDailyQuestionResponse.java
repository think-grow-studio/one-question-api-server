package site.one_question.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘의 질문 응답")
public record ServeDailyQuestionResponse(
        @Schema(description = "질문 ID", example = "43")
        Long id,

        @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String question,

        @Schema(description = "질문 1년 사이클 횟수", example = "1")
        Long questionCycle,

        @Schema(description = "질문 변경 횟수", example = "2")
        Long changeCount
) {
}
