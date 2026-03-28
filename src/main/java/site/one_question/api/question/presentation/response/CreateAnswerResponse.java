package site.one_question.api.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.api.question.domain.DailyQuestionAnswer;

@Schema(description = "답변 작성 응답")
public record CreateAnswerResponse(
        @Schema(description = "답변 ID", example = "156")
        Long dailyAnswerId,

        @Schema(description = "답변 내용", example = "새로운 시작의 날")
        String content,

        @Schema(description = "답변 작성 시각 (클라이언트 로컬 타임존 기준)", example = "2024-01-15T14:30:00")
        LocalDateTime answeredAt,

        @Schema(description = "공개 게시 여부", example = "false")
        boolean published
) {
    public static CreateAnswerResponse from(DailyQuestionAnswer answer, String timezone) {
        return from(answer, timezone, false);
    }

    public static CreateAnswerResponse from(DailyQuestionAnswer answer, String timezone, boolean published) {
        LocalDateTime answeredAt = LocalDateTime.ofInstant(
                answer.getAnsweredAt(), ZoneId.of(timezone));
        return new CreateAnswerResponse(answer.getId(), answer.getContent(), answeredAt, published);
    }
}
