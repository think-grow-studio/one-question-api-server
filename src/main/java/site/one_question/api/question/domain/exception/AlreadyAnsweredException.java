package site.one_question.api.question.domain.exception;

public class AlreadyAnsweredException extends QuestionException {

    public AlreadyAnsweredException() {
        super(QuestionExceptionSpec.ALREADY_ANSWERED);
    }
}
