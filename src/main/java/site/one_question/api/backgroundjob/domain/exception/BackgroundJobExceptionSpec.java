package site.one_question.api.backgroundjob.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum BackgroundJobExceptionSpec implements ExceptionSpec {
    IDEMPOTENCY_KEY_INVALID(
            HttpStatus.BAD_REQUEST,
            "BACKGROUND-JOB-001",
            "백그라운드 작업 멱등키 오류",
            "error.background-job.idempotency-key-invalid"
    ),
    REQUEST_HASH_INVALID(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "BACKGROUND-JOB-002",
            "백그라운드 작업 요청 해시 오류",
            "error.background-job.request-hash-invalid"
    ),
    REQUEST_HASH_CONFLICT(
            HttpStatus.CONFLICT,
            "BACKGROUND-JOB-003",
            "백그라운드 작업 멱등키 재사용 충돌",
            "error.background-job.request-hash-conflict"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
