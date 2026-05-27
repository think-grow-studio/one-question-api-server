package site.one_question.api.publicquestion.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;

@Schema(description = "공개 일일 질문 답변 항목")
public record PublicDailyQuestionAnswerDto(
        @Schema(description = "답변 ID", example = "10")
        Long publicDailyQuestionAnswerId,

        @Schema(description = "답변 내용")
        String content,

        @Schema(description = "익명 닉네임", example = "달리는 토끼")
        String anonymousNickname,

        @Schema(description = "답변 작성 시간 (수정된 경우 수정 시간, 작성 당시 클라이언트 로컬 타임존 기준)", example = "2026-05-22T14:30:00")
        LocalDateTime answeredAt,

        @Schema(description = "좋아요 수", example = "5")
        long likeCount,

        @Schema(description = "본인 좋아요 여부", example = "true")
        boolean liked
) {
    public static PublicDailyQuestionAnswerDto from(PublicDailyQuestionAnswer answer, long likeCount, boolean liked) {
        Instant timestamp = answer.getUpdatedAt();
        if (timestamp == null) {
            timestamp = answer.getAnsweredAt();
        }
        LocalDateTime answeredAt = LocalDateTime.ofInstant(timestamp, ZoneId.of(answer.getTimezone()));
        return new PublicDailyQuestionAnswerDto(
                answer.getId(),
                answer.getContent(),
                answer.getAnonymousNickname(),
                answeredAt,
                likeCount,
                liked
        );
    }
}
