package site.one_question.api.backgroundjob.domain;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * BackgroundJob 발행 재시도 정책의 단일 출처.
 * 발행 실패(Processor)와 lease 만료 복구(Application) 양쪽이 이 정책 하나로
 * "실패 횟수 증가 → 지수 백오프 재예약 → 소진 시 FAILED" 결정을 공유한다.
 *
 * <p>백오프는 {@code baseRetryDelay * 2^currentRetryCount}(기본 1·2·4·8분)이며,
 * 시도 횟수가 {@code maxAttempts}(기본 5)에 도달하면 소진으로 보고 FAILED로 전이한다.
 */
@Component
public class PublishRetryPolicy {

    private static final int MAX_ERROR_REASON_LENGTH = 255;
    private static final String PUBLISH_FAILED_ERROR_CODE = "PUBLISH_FAILED";

    private final int maxAttempts;
    private final Duration baseRetryDelay;

    public PublishRetryPolicy(
            @Value("${app.background-job.publisher.max-attempts:5}")
            int maxAttempts,
            @Value("${app.background-job.publisher.base-retry-delay-minutes:1}")
            long baseRetryDelayMinutes
    ) {
        this.maxAttempts = maxAttempts;
        this.baseRetryDelay = Duration.ofMinutes(baseRetryDelayMinutes);
    }

    /**
     * @param currentAttemptCount 이번 시도의 번호(1부터). 선점 CAS 에서 이미 증가된 값이므로
     *                            여기서 다시 더하지 않는다.
     * @param now                 상태 전이 기준 시각
     * @param errorReason         실패 원인 메시지 (255자로 잘라 저장)
     */
    public PublishFailureTransition onFailure(int currentAttemptCount, Instant now, String errorReason) {
        boolean exhausted = currentAttemptCount >= maxAttempts;
        BackgroundJobStatus nextStatus = exhausted
                ? BackgroundJobStatus.FAILED
                : BackgroundJobStatus.PENDING;
        // 소진 시엔 null 을 돌려 예약 시각을 그대로 둔다(FAILED 는 어차피 발행 대상이 아니다).
        Instant publishScheduledAt = exhausted
                ? null
                : now.plus(baseRetryDelay.multipliedBy(1L << (currentAttemptCount - 1)));
        Instant finishedAt = exhausted ? now : null;
        return new PublishFailureTransition(
                nextStatus,
                publishScheduledAt,
                finishedAt,
                PUBLISH_FAILED_ERROR_CODE,
                truncate(errorReason),
                exhausted);
    }

    private static String truncate(String errorReason) {
        if (errorReason == null || errorReason.length() <= MAX_ERROR_REASON_LENGTH) {
            return errorReason;
        }
        return errorReason.substring(0, MAX_ERROR_REASON_LENGTH);
    }
}
