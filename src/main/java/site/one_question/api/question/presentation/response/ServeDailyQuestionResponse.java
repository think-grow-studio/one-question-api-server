package site.one_question.api.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;

@Schema(description = "오늘의 질문 응답")
public record ServeDailyQuestionResponse(
        @Schema(description = "오늘의 질문 ID", example = "43")
        Long dailyQuestionId,

        @Schema(description = "질문 ID", example = "7")
        Long questionId,

        @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String content,

        @Schema(description = "질문 보충 설명", example = "ex) 폭풍 전야", nullable = true)
        String description,

        @Schema(description = "질문 1년 사이클 횟수", example = "1")
        Long questionCycle,

        @Schema(description = "질문 변경 횟수", example = "2")
        Long changeCount,

        @Schema(description = "좋아요 여부", example = "false")
        boolean liked
) {
    public static ServeDailyQuestionResponse from(
            DailyQuestion dailyQuestion,
            Question question,
            QuestionCycle cycle,
            boolean liked
    ) {
        return new ServeDailyQuestionResponse(
                dailyQuestion.getId(),
                question.getId(),
                question.getContent(),
                question.getDescription(),
                (long) cycle.getCycleNumber(),
                (long) dailyQuestion.getChangeCount(),
                liked
        );
    }
}
