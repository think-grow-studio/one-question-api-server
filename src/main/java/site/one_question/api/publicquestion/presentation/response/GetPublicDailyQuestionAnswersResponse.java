package site.one_question.api.publicquestion.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;

@Schema(description = "공개 일일 질문 답변 목록 (커서 기반) 응답")
public record GetPublicDailyQuestionAnswersResponse(
        @Schema(description = "답변 항목 목록")
        List<PublicDailyQuestionAnswerDto> items,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 커서 (hasNext = false 면 null)")
        Cursor nextCursor
) {
    @Schema(description = "커서")
    public record Cursor(
            @Schema(description = "마지막 항목의 answeredAt (Instant, UTC)")
            Instant answeredAt,

            @Schema(description = "마지막 항목의 답변 ID")
            Long id
    ) {
        public static Cursor from(PublicDailyQuestionAnswer last) {
            return new Cursor(last.getAnsweredAt(), last.getId());
        }
    }

    public static GetPublicDailyQuestionAnswersResponse from(
            List<PublicDailyQuestionAnswer> answers,
            Map<Long, Long> likeCounts,
            Set<Long> likedAnswerIds,
            boolean hasNext
    ) {
        List<PublicDailyQuestionAnswerDto> dtos = answers.stream()
                .map(a -> PublicDailyQuestionAnswerDto.from(
                        a,
                        likeCounts.getOrDefault(a.getId(), 0L),
                        likedAnswerIds.contains(a.getId())))
                .toList();

        Cursor nextCursor = null;
        if (hasNext && !answers.isEmpty()) {
            nextCursor = Cursor.from(answers.get(answers.size() - 1));
        }

        return new GetPublicDailyQuestionAnswersResponse(dtos, hasNext, nextCursor);
    }
}
