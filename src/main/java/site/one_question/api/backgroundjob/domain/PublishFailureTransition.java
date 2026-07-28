package site.one_question.api.backgroundjob.domain;

import java.time.Instant;

/**
 * 발행 실패(또는 lease 만료 복구) 시 적용할 상태 전이 결정.
 * {@link PublishRetryPolicy}가 재시도 횟수와 백오프를 계산해 만든다.
 * 발행 실패 경로(Processor)와 만료 복구 경로(Application)가 같은 정책을 공유하도록,
 * 상태 변경 쿼리에 필요한 값 전부를 한 스냅샷으로 담는다.
 */
public record PublishFailureTransition(
        BackgroundJobStatus nextStatus,
        int retryCount,
        Instant nextRetryAt,
        Instant finishedAt,
        String errorCode,
        String errorReason,
        boolean exhausted
) {
}
