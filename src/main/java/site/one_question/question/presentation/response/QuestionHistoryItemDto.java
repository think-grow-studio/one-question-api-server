package site.one_question.question.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.DailyQuestionAnswer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
        @Schema(description = "일일 질문 ID", example = "43")
        Long dailyQuestionId,

        @Schema(description = "질문 내용", example = "오늘 하루에 제목을 붙인다면?")
        String content,

        @Schema(description = "질문 보충 설명", example = "ex) 폭풍 전야", nullable = true)
        String description,

        @Schema(description = "질문 주기 (며칠마다 반복되는지)", example = "1")
        Long questionCycle,

        @Schema(description = "질문 변경 횟수", example = "2")
        Long changeCount
    ) {}

    @Schema(description = "답변 정보")
    public record AnswerInfoDto(
            @Schema(description = "일일 질문에 대한 답변 ID", example = "156")
            Long dailyAnswerId,

            @Schema(description = "답변 내용", example = "새로운 시작의 날")
            String content,

            @Schema(description = "답변 작성 시간", example = "2024-01-15T14:30:00")
            LocalDateTime answeredAt
    ) {
        public static AnswerInfoDto from(DailyQuestionAnswer answer, String timezone) {
            return new AnswerInfoDto(
                answer.getId(),
                answer.getContent(),
                answer.getAnsweredAt().atZone(ZoneId.of(timezone)).toLocalDateTime()
            );
        }
    }

    public static QuestionHistoryItemDto noQuestion(LocalDate date) {
        return new QuestionHistoryItemDto(date, Status.NO_QUESTION, null, null);
    }

    public static QuestionHistoryItemDto from(DailyQuestion dailyQuestion, String timezone) {
        QuestionInfoDto questionInfo = new QuestionInfoDto(
            dailyQuestion.getId(),
            dailyQuestion.getQuestion().getContent(),
            dailyQuestion.getQuestion().getDescription(),
            (long) dailyQuestion.getQuestionCycle().getCycleNumber(),
            (long) dailyQuestion.getChangeCount()
        );

        DailyQuestionAnswer answer = dailyQuestion.getAnswer();
        if (answer == null) {
            return new QuestionHistoryItemDto(
                dailyQuestion.getDate(),
                Status.UNANSWERED,
                questionInfo,
                null
            );
        }

        return new QuestionHistoryItemDto(
            dailyQuestion.getDate(),
            Status.ANSWERED,
            questionInfo,
            AnswerInfoDto.from(answer, timezone)
        );
    }
}
