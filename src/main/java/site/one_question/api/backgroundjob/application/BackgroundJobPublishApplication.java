package site.one_question.api.backgroundjob.application;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisherRegistry;
import site.one_question.api.backgroundjob.domain.BackgroundJobService;
import site.one_question.api.backgroundjob.domain.ExpiredPublishClaim;
import site.one_question.api.backgroundjob.domain.PendingPublishTarget;
import site.one_question.api.backgroundjob.domain.PublishFailureTransition;
import site.one_question.api.backgroundjob.domain.PublishRetryPolicy;

@Slf4j
@Service
public class BackgroundJobPublishApplication {

    private static final int PUBLISH_BATCH_SIZE = 50;

    private final BackgroundJobPublisherRegistry publisherRegistry;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobPublishProcessor processor;
    private final PublishRetryPolicy retryPolicy;
    private final TransactionTemplate txTemplate;

    public BackgroundJobPublishApplication(
            BackgroundJobPublisherRegistry publisherRegistry,
            BackgroundJobService backgroundJobService,
            BackgroundJobPublishProcessor processor,
            PublishRetryPolicy retryPolicy,
            PlatformTransactionManager transactionManager
    ) {
        this.publisherRegistry = publisherRegistry;
        this.backgroundJobService = backgroundJobService;
        this.processor = processor;
        this.retryPolicy = retryPolicy;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void publishPendingJobs() {
        Instant now = Instant.now();
        recoverExpiredClaims(now);
        publishPendingJobs(now);
    }

    private void recoverExpiredClaims(Instant recoveryNow) {
        List<ExpiredPublishClaim> expiredClaims =
                backgroundJobService.findExpiredPublishClaims(recoveryNow, PUBLISH_BATCH_SIZE);
        if (expiredClaims.isEmpty()) {
            return;
        }
        log.warn("만료된 BackgroundJob 발행 claim {}건 발견, 복구를 시도합니다", expiredClaims.size());
        for (ExpiredPublishClaim expiredClaim : expiredClaims) {
            try {
                recoverExpiredClaim(expiredClaim, recoveryNow);
            } catch (Exception e) {
                log.error("만료된 BackgroundJob 발행 claim 복구 실패: jobId={}",
                        expiredClaim.id(), e);
            }
        }
    }

    private void recoverExpiredClaim(
            ExpiredPublishClaim expiredClaim,
            Instant recoveryNow
    ) {
        Exception cause = new IllegalStateException(
                "BackgroundJob 발행 claim이 만료되었습니다: jobId=" + expiredClaim.id());
        PublishFailureTransition transition =
                retryPolicy.onFailure(expiredClaim.publishAttemptCount(), recoveryNow, cause.getMessage());

      int updated = txTemplate.execute(status ->
          backgroundJobService.recoverExpiredPublish(
              expiredClaim.id(),
              expiredClaim.claimId(),
              recoveryNow,
              transition.nextStatus(),
              transition.publishScheduledAt(),
              transition.finishedAt(),
              transition.errorCode(),
              transition.errorReason(),
              recoveryNow));
      if (updated != 1) {
            log.debug("만료된 claim 복구 스킵(이미 다른 프로세스가 처리함): jobId={}", expiredClaim.id());
            return;
        }
        if (transition.exhausted()) {
            log.warn("만료된 claim 복구 후 재시도 소진으로 BackgroundJob 실패 처리: jobId={}", expiredClaim.id());
            BackgroundJobPublisher publisher = publisherRegistry.get(expiredClaim.jobType());
            notifyExhausted(publisher, expiredClaim.id(), cause);
        } else {
            log.info("만료된 claim 복구 후 재시도 예약: jobId={}, publishAttemptCount={}, publishScheduledAt={}",
                    expiredClaim.id(), expiredClaim.publishAttemptCount(), transition.publishScheduledAt());
        }
    }

    private void publishPendingJobs(Instant now) {
        List<PendingPublishTarget> targets =
                backgroundJobService.findPendingPublishTargets(now, PUBLISH_BATCH_SIZE);
        if (targets.isEmpty()) {
            return;
        }
        log.info("발행 대상 BackgroundJob {}건 조회됨", targets.size());
        int queued = 0;
        int retryScheduled = 0;
        int exhausted = 0;
        int skipped = 0;
        for (PendingPublishTarget target : targets) {
            BackgroundJobPublisher publisher = publisherRegistry.get(target.jobType());
            try {
                PublishAttemptResult result = processor.process(target.id(), publisher);
                switch (result.kind()) {
                    case QUEUED -> queued++;
                    case RETRY_SCHEDULED -> retryScheduled++;
                    case SKIPPED -> skipped++;
                    case EXHAUSTED -> {
                        exhausted++;
                        log.warn("BackgroundJob 발행 재시도 소진, FAILED 처리: jobId={}",
                                target.id(), result.cause());
                        notifyExhausted(publisher, target.id(), result.cause());
                    }
                }
            } catch (Exception e) {
                log.error("BackgroundJob 발행 처리 실패: jobId={}", target.id(), e);
            }
        }
        log.info("BackgroundJob 발행 배치 완료: 전체={}, 성공={}, 재시도예약={}, 소진={}, 스킵={}",
                targets.size(), queued, retryScheduled, exhausted, skipped);
    }

    private void notifyExhausted(
            BackgroundJobPublisher publisher,
            Long jobId,
            Exception cause
    ) {
        try {
            publisher.onPublishExhausted(jobId, cause);
        } catch (Exception callbackException) {
            log.error("BackgroundJob 발행 실패 후처리 실패: jobId={}", jobId, callbackException);
        }
    }
}
