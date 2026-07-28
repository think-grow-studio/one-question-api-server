package site.one_question.integrate.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.one_question.api.backgroundjob.application.BackgroundJobPublishProcessor;
import site.one_question.api.backgroundjob.application.PublishAttemptResult;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobStatus;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.ClaimedBackgroundJob;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("백그라운드 작업 발행 Processor 통합 테스트")
class BackgroundJobPublishProcessorIntegrateTest extends IntegrateTest {

    @Autowired
    private BackgroundJobPublishProcessor processor;

    private BackgroundJob job;

    @BeforeEach
    void setUp() {
        job = testBackgroundJobUtils.createSave();
    }

    @Test
    @DisplayName("외부 발행은 트랜잭션 없이 실행되고 성공하면 QUEUED가 된다")
    void publish_runs_without_transaction_and_completes_as_queued() {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        BackgroundJobPublisher publisher = publisher(claimed ->
                transactionActive.set(
                        TransactionSynchronizationManager.isActualTransactionActive()));

        PublishAttemptResult outcome = processor.process(job.getId(), publisher);

        assertThat(transactionActive).isFalse();
        assertThat(outcome.kind()).isEqualTo(PublishAttemptResult.Kind.QUEUED);
        BackgroundJob queued = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(queued.getPublishClaimId()).isNull();
        assertThat(queued.getPublishClaimUntil()).isNull();
    }

    @Test
    @DisplayName("발행 실패는 PENDING으로 복귀하고 1분 뒤 재시도한다")
    void transient_failure_schedules_retry() {
        Instant before = Instant.now();
        BackgroundJobPublisher publisher = publisher(claimed -> {
            throw new IllegalStateException("sqs unavailable");
        });

        PublishAttemptResult outcome = processor.process(job.getId(), publisher);

        assertThat(outcome.kind()).isEqualTo(PublishAttemptResult.Kind.RETRY_SCHEDULED);
        BackgroundJob pending = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(pending.getRetryCount()).isEqualTo(1);
        assertThat(pending.getNextRetryAt())
                .isBetween(before.plusSeconds(50), before.plusSeconds(70));
        assertThat(pending.getPublishClaimId()).isNull();
    }

    @Test
    @DisplayName("5번째 실패는 원인을 포함한 EXHAUSTED를 반환하고 callback은 호출하지 않는다")
    void fifth_failure_returns_exhausted_without_callback() {
        testBackgroundJobUtils.recordPreviousPublishFailures(
                job, 4, Instant.now().minusSeconds(1));
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        IllegalStateException failure = new IllegalStateException("sqs unavailable");
        BackgroundJobPublisher publisher = new BackgroundJobPublisher() {
            @Override
            public BackgroundJobType supportedType() {
                return BackgroundJobType.ANALYSIS_REPORT;
            }

            @Override
            public void publish(ClaimedBackgroundJob claimed) {
                throw failure;
            }

            @Override
            public void onPublishExhausted(Long jobId, Exception cause) {
                callbackCalled.set(true);
            }
        };

        PublishAttemptResult outcome = processor.process(job.getId(), publisher);

        assertThat(outcome.kind()).isEqualTo(PublishAttemptResult.Kind.EXHAUSTED);
        assertThat(outcome.cause()).isSameAs(failure);
        assertThat(callbackCalled).isFalse();
        assertThat(backgroundJobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.FAILED);
    }

    private BackgroundJobPublisher publisher(PublishAction action) {
        return new BackgroundJobPublisher() {
            @Override
            public BackgroundJobType supportedType() {
                return BackgroundJobType.ANALYSIS_REPORT;
            }

            @Override
            public void publish(ClaimedBackgroundJob claimed) {
                action.publish(claimed);
            }
        };
    }

    @FunctionalInterface
    private interface PublishAction {
        void publish(ClaimedBackgroundJob claimed);
    }
}
