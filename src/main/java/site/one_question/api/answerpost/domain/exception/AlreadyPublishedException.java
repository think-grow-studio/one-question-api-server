package site.one_question.api.answerpost.domain.exception;

import java.util.Map;

public class AlreadyPublishedException extends AnswerPostException {

    public AlreadyPublishedException(Long questionAnswerId) {
        super(AnswerPostExceptionSpec.ALREADY_PUBLISHED, Map.of("questionAnswerId", questionAnswerId));
    }
}
