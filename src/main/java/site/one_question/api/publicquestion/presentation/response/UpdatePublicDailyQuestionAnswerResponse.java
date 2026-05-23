package site.one_question.api.publicquestion.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;

@Schema(description = "공개 일일 질문 답변 수정 응답")
public record UpdatePublicDailyQuestionAnswerResponse(
        @Schema(description = "답변 ID", example = "10")
        Long publicDailyQuestionAnswerId,

        @Schema(description = "수정된 답변 내용", example = "오늘은 정말 특별한 하루였다")
        String content,

        @Schema(description = "익명 닉네임", example = "달리는 토끼")
        String anonymousNickname,

        @Schema(description = "답변 수정 시각 (클라이언트 로컬 타임존 기준)", example = "2026-05-22T18:45:00")
        LocalDateTime answeredAt
) {
    public static UpdatePublicDailyQuestionAnswerResponse from(PublicDailyQuestionAnswer answer, String timezone) {
        LocalDateTime updatedAt = LocalDateTime.ofInstant(
                answer.getUpdatedAt(), ZoneId.of(timezone));
        return new UpdatePublicDailyQuestionAnswerResponse(
                answer.getId(),
                answer.getContent(),
                answer.getAnonymousNickname(),
                updatedAt
        );
    }
}
