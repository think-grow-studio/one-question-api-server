package site.one_question.api.publicquestion.domain.exception;

public class EmptyAnswerContentException extends PublicQuestionException {

    public EmptyAnswerContentException() {
        super(PublicQuestionExceptionSpec.EMPTY_ANSWER_CONTENT);
    }
}
