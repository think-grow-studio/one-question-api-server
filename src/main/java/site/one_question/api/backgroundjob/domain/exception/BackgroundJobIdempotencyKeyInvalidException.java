package site.one_question.api.backgroundjob.domain.exception;

public class BackgroundJobIdempotencyKeyInvalidException extends BackgroundJobException {

    public BackgroundJobIdempotencyKeyInvalidException() {
        super(BackgroundJobExceptionSpec.IDEMPOTENCY_KEY_INVALID);
    }
}
