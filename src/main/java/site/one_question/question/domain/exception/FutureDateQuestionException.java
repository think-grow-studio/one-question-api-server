package site.one_question.question.domain.exception;

public class FutureDateQuestionException extends QuestionException {

    public FutureDateQuestionException() {
        super(QuestionExceptionSpec.FUTURE_DATE_QUESTION);
    }
}
