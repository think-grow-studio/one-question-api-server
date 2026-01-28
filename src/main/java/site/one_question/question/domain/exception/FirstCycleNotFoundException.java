package site.one_question.question.domain.exception;

public class FirstCycleNotFoundException extends QuestionException {

    public FirstCycleNotFoundException() {
        super(QuestionExceptionSpec.FIRST_CYCLE_NOT_FOUND);
    }
}
