package site.one_question.api.backgroundjob.domain.exception;

import java.util.Map;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;

public class BackgroundJobRequestHashConflictException extends BackgroundJobException {

    public BackgroundJobRequestHashConflictException(
            Long memberId,
            BackgroundJobType jobType,
            String idempotencyKey
    ) {
        super(
                BackgroundJobExceptionSpec.REQUEST_HASH_CONFLICT,
                Map.of(
                        "memberId", memberId,
                        "jobType", jobType,
                        "idempotencyKey", idempotencyKey
                )
        );
    }
}
