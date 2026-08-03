package site.one_question.api.backgroundjob.application;

/**
 * 한 건의 발행 시도 결과. 결과 종류({@link Kind})와, EXHAUSTED일 때 후처리에 넘길 원인을 담는다.
 */
public record PublishAttemptResult(Kind kind, Exception cause) {

    public enum Kind {
        /** 선점 실패 또는 stale CAS 등으로 이번 시도에서 아무 상태도 바꾸지 않음. */
        SKIPPED,
        /** 발행 성공, QUEUED로 전이됨. */
        QUEUED,
        /** 일시 실패, PENDING으로 복귀하고 재시도 예약됨. */
        RETRY_SCHEDULED,
        /** 재시도 소진, FAILED로 전이됨. cause에 최종 실패 원인이 담긴다. */
        EXHAUSTED
    }

    public static PublishAttemptResult skipped() {
        return new PublishAttemptResult(Kind.SKIPPED, null);
    }

    public static PublishAttemptResult queued() {
        return new PublishAttemptResult(Kind.QUEUED, null);
    }

    public static PublishAttemptResult retryScheduled() {
        return new PublishAttemptResult(Kind.RETRY_SCHEDULED, null);
    }

    public static PublishAttemptResult exhausted(Exception cause) {
        return new PublishAttemptResult(Kind.EXHAUSTED, cause);
    }

    public boolean isExhausted() {
        return kind == Kind.EXHAUSTED;
    }
}
