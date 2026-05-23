package site.one_question.api.publicquestion.domain.exception;

import java.util.Map;

public class PublicDailyQuestionAnswerNotFoundException extends PublicQuestionException {

    public PublicDailyQuestionAnswerNotFoundException(Long pdqId, Long memberId) {
        super(
                PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND,
                Map.of("pdqId", pdqId, "memberId", memberId)
        );
    }

    public PublicDailyQuestionAnswerNotFoundException(Long answerId) {
        super(
                PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND,
                Map.of("answerId", answerId)
        );
    }
}
