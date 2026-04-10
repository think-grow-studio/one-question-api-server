package site.one_question.api.question.domain.exception;

public class CandidateNotFoundException extends QuestionException {

    public CandidateNotFoundException() {
        super(QuestionExceptionSpec.CANDIDATE_NOT_FOUND);
    }
}
