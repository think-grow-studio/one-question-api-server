package site.one_question.api.publicquestion.presentation.response;

import java.time.LocalDate;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;

public record GetPublicDailyQuestionResponse(
        Long publicDailyQuestionId,
        Long questionId,
        String content,
        String description,
        LocalDate questionDate
) {
    public static GetPublicDailyQuestionResponse from(PublicDailyQuestion pdq) {
        return new GetPublicDailyQuestionResponse(
                pdq.getId(),
                pdq.getQuestion().getId(),
                pdq.getQuestion().getContent(),
                pdq.getQuestion().getDescription(),
                pdq.getQuestionDate()
        );
    }
}
