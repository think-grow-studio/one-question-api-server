package site.one_question.api.publicquestion.domain.exception;

import java.util.Map;

public class PublicDailyQuestionAnswerAlreadyExistsException extends PublicQuestionException {

    public PublicDailyQuestionAnswerAlreadyExistsException(Long pdqId, Long memberId) {
        super(
                PublicQuestionExceptionSpec.PUBLIC_ANSWER_ALREADY_EXISTS,
                Map.of("pdqId", pdqId, "memberId", memberId)
        );
    }
}
