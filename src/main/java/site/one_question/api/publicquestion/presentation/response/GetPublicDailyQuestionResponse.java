package site.one_question.api.publicquestion.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;

@Schema(description = "공개 일일 질문 조회 응답")
public record GetPublicDailyQuestionResponse(
        @Schema(description = "공개 일일 질문 ID", example = "1")
        Long publicDailyQuestionId,

        @Schema(description = "질문 ID", example = "100")
        Long questionId,

        @Schema(description = "질문 내용")
        String content,

        @Schema(description = "질문 설명")
        String description,

        @Schema(description = "질문 노출 날짜 (UTC)", example = "2026-05-22")
        LocalDate questionDate,

        @Schema(description = "내 답변 (작성하지 않았다면 null)")
        MyAnswerDto myAnswer
) {
    @Schema(description = "내 답변 정보")
    public record MyAnswerDto(
            @Schema(description = "답변 ID", example = "10")
            Long publicDailyQuestionAnswerId,

            @Schema(description = "답변 내용", example = "오늘 가장 인상 깊었던 일은...")
            String content,

            @Schema(description = "익명 닉네임", example = "달리는 토끼")
            String anonymousNickname,

            @Schema(description = "답변 작성 시간 (수정된 경우 수정 시간, 작성 당시 클라이언트 로컬 타임존 기준)", example = "2026-05-22T14:30:00")
            LocalDateTime answeredAt
    ) {
        public static MyAnswerDto from(PublicDailyQuestionAnswer answer) {
            Instant timestamp = answer.getUpdatedAt() != null
                    ? answer.getUpdatedAt()
                    : answer.getAnsweredAt();
            LocalDateTime answeredAt = LocalDateTime.ofInstant(
                    timestamp, ZoneId.of(answer.getTimezone()));
            return new MyAnswerDto(
                    answer.getId(),
                    answer.getContent(),
                    answer.getAnonymousNickname(),
                    answeredAt
            );
        }
    }

    public static GetPublicDailyQuestionResponse from(PublicDailyQuestion pdq, PublicDailyQuestionAnswer answer) {
        return new GetPublicDailyQuestionResponse(
                pdq.getId(),
                pdq.getQuestion().getId(),
                pdq.getQuestion().getContent(),
                pdq.getQuestion().getDescription(),
                pdq.getQuestionDate(),
                answer == null ? null : MyAnswerDto.from(answer)
        );
    }
}
