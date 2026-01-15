package site.one_question.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "질문 히스토리 항목")
public record QuestionHistoryItemDto(
        @Schema(description = "해당 날짜", example = "2024-01-15")
        LocalDate date,

        @Schema(description = "상태", example = "ANSWERED",
                allowableValues = {"ANSWERED", "UNANSWERED", "NO_QUESTION"})
        Status status,

        @Schema(description = "질문 정보 (status가 NO_QUESTION이면 null)")
        QuestionInfoDto question,

        @Schema(description = "답변 정보 (status가 ANSWERED일 때만 존재)")
        AnswerInfoDto answer
) {
    public enum Status {
        @Schema(description = "질문 받음 + 답변 완료")
        ANSWERED,

        @Schema(description = "질문 받음 + 답변 없음")
        UNANSWERED,

        @Schema(description = "질문 없음")
        NO_QUESTION
    }

    @Schema(description = "질문 정보")
    public record QuestionInfoDto(
        @Schema(description = "질문 ID", example = "43")
        Long id,

        @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String question,

        @Schema(description = "질문 주기 (며칠마다 반복되는지)", example = "1")
        Long questionCycle,

        @Schema(description = "질문 변경 횟수", example = "2")
        Long changeCount
    ) {}

    @Schema(description = "답변 정보")
    public record AnswerInfoDto(
            @Schema(description = "답변 ID", example = "156")
            Long id,

            @Schema(description = "답변 내용", example = "새로운 시작의 날")
            String answer,

            @Schema(description = "답변 작성 시간", example = "2024-01-15T14:30:00")
            String answeredAt
    ) {}
}
