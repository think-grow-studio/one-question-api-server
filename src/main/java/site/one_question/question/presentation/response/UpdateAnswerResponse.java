package site.one_question.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.question.domain.DailyQuestionAnswer;

@Schema(description = "답변 수정 응답")
public record UpdateAnswerResponse(
        @Schema(description = "답변 ID", example = "156")
        Long dailyAnswerId,

        @Schema(description = "수정된 답변 내용", example = "오늘은 정말 특별한 하루였다")
        String content,

        @Schema(description = "답변 수정 시각 (클라이언트 로컬 타임존 기준)", example = "2024-01-15T18:45:00")
        LocalDateTime answeredAt
) {
    public static UpdateAnswerResponse from(DailyQuestionAnswer answer, String timezone) {
        LocalDateTime updatedAt = LocalDateTime.ofInstant(
                answer.getUpdatedAt(), ZoneId.of(timezone));
        return new UpdateAnswerResponse(answer.getId(), answer.getContent(), updatedAt);
    }
}
