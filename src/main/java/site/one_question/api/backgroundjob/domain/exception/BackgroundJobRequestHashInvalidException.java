package site.one_question.api.backgroundjob.domain.exception;

import java.util.Map;

public class BackgroundJobRequestHashInvalidException extends BackgroundJobException {

    public BackgroundJobRequestHashInvalidException(String requestHash) {
        super(
                BackgroundJobExceptionSpec.REQUEST_HASH_INVALID,
                Map.of("requestHash", requestHash == null ? "null" : requestHash)
        );
    }
}
