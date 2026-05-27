package site.one_question.api.publicquestion.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;

@Schema(description = "공개 일일 질문 답변 작성 응답")
public record CreatePublicDailyQuestionAnswerResponse(
        @Schema(description = "답변 ID", example = "10")
        Long publicDailyQuestionAnswerId,

        @Schema(description = "답변 내용", example = "오늘 가장 인상 깊었던 일은...")
        String content,

        @Schema(description = "익명 닉네임", example = "달리는 토끼")
        String anonymousNickname,

        @Schema(description = "답변 작성 시각 (클라이언트 로컬 타임존 기준)", example = "2026-05-22T14:30:00")
        LocalDateTime answeredAt
) {
    public static CreatePublicDailyQuestionAnswerResponse from(PublicDailyQuestionAnswer answer, String timezone) {
        LocalDateTime answeredAt = LocalDateTime.ofInstant(answer.getAnsweredAt(), ZoneId.of(timezone));
        return new CreatePublicDailyQuestionAnswerResponse(
                answer.getId(),
                answer.getContent(),
                answer.getAnonymousNickname(),
                answeredAt
        );
    }
}
