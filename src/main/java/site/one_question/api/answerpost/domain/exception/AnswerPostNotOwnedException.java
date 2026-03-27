package site.one_question.api.answerpost.domain.exception;

import java.util.Map;

public class AnswerPostNotOwnedException extends AnswerPostException {

    public AnswerPostNotOwnedException(Long answerPostId) {
        super(AnswerPostExceptionSpec.ANSWER_POST_NOT_OWNED, Map.of("answerPostId", answerPostId));
    }
}
