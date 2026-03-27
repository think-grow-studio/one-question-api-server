package site.one_question.api.answerpost.domain.exception;

import java.util.Map;

public class AnswerPostNotFoundException extends AnswerPostException {

    public AnswerPostNotFoundException(Long answerPostId) {
        super(AnswerPostExceptionSpec.ANSWER_POST_NOT_FOUND, Map.of("answerPostId", answerPostId));
    }
}
