package site.one_question.api.backgroundjob.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobService;
import site.one_question.api.backgroundjob.domain.ClaimedBackgroundJob;
import site.one_question.api.backgroundjob.domain.PublishFailureTransition;
import site.one_question.api.backgroundjob.domain.PublishRetryPolicy;

@Slf4j
@Component
public class BackgroundJobPublishProcessor {

    private final BackgroundJobService backgroundJobService;
    private final PublishRetryPolicy retryPolicy;
    private final TransactionTemplate txTemplate;
    private final Duration claimDuration;

    public BackgroundJobPublishProcessor(
            BackgroundJobService backgroundJobService,
            PublishRetryPolicy retryPolicy,
            PlatformTransactionManager transactionManager,
            @Value("${app.background-job.publisher.claim-duration-minutes:1}")
            long claimDurationMinutes
    ) {
        this.backgroundJobService = backgroundJobService;
        this.retryPolicy = retryPolicy;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.claimDuration = Duration.ofMinutes(claimDurationMinutes);
    }

    /**
     * 한 건의 발행을 처리한다. SQS 외부 호출을 DB 트랜잭션 밖에서 실행하기 위해
     * <b>반드시 트랜잭션 밖에서 호출</b>해야 한다(스케줄러 진입점이 이를 보장한다).
     * 선점·완료·실패 등 DB 상태 변경만 각각 짧은 REQUIRES_NEW 트랜잭션으로 처리하고,
     * 그 사이의 {@code publisher.publish()}(SQS 호출)는 트랜잭션 밖에서 실행된다.
     */
    public PublishAttemptResult process(Long jobId, BackgroundJobPublisher publisher) {
        Instant claimedAt = Instant.now();
        String claimId = UUID.randomUUID().toString();
        Optional<ClaimedBackgroundJob> claimed = txTemplate.execute(status ->
                backgroundJobService.claimForPublishing(
                        jobId,
                        claimId,
                        claimedAt.plus(claimDuration),
                        claimedAt));
        if (claimed.isEmpty()) {
            return PublishAttemptResult.skipped();
        }

        ClaimedBackgroundJob job = claimed.get();
        try {
            publisher.publish(job);
        } catch (Exception cause) {
            return recordFailure(job, cause);
        }

        int updated = txTemplate.execute(status ->
                backgroundJobService.completePublish(job.id(), job.claimId(), Instant.now()));
        return updated == 1
                ? PublishAttemptResult.queued()
                : PublishAttemptResult.skipped();
    }

    private PublishAttemptResult recordFailure(
            ClaimedBackgroundJob job,
            Exception cause
    ) {
        Instant now = Instant.now();
        PublishFailureTransition transition =
                retryPolicy.onFailure(job.publishAttemptCount(), now, cause.getMessage());

        int updated = txTemplate.execute(status ->
                backgroundJobService.recordPublishFailure(
                        job.id(),
                        job.claimId(),
                        transition.nextStatus(),
                        transition.publishScheduledAt(),
                        transition.finishedAt(),
                        transition.errorCode(),
                        transition.errorReason(),
                        now));
        if (updated != 1) {
            return PublishAttemptResult.skipped();
        }
        if (transition.exhausted()) {
            return PublishAttemptResult.exhausted(cause);
        }
        log.warn("BackgroundJob 발행 실패, 재시도 예약: jobId={}, publishAttemptCount={}, publishScheduledAt={}",
                job.id(), job.publishAttemptCount(), transition.publishScheduledAt(), cause);
        return PublishAttemptResult.retryScheduled();
    }
}
