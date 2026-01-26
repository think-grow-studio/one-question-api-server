package site.one_question.question.domain.exception;

public class BeforeSignupDateException extends QuestionException {

    public BeforeSignupDateException() {
        super(QuestionExceptionSpec.BEFORE_SIGNUP_DATE);
    }
}
