package site.one_question.question.domain.exception;

public class AlreadyAnsweredException extends QuestionException {

    public AlreadyAnsweredException() {
        super(QuestionExceptionSpec.ALREADY_ANSWERED);
    }
}
