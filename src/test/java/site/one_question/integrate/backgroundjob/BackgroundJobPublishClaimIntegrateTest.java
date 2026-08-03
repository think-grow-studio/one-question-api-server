package site.one_question.integrate.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.metamodel.Attribute;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobStatus;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("백그라운드 작업 발행 선점 통합 테스트")
class BackgroundJobPublishClaimIntegrateTest extends IntegrateTest {

    private BackgroundJob job;

    @BeforeEach
    void setUp() {
        job = testBackgroundJobUtils.createSave();
    }

    @Test
    @DisplayName("BackgroundJob은 발행 선점 필드를 매핑한다")
    void background_job_maps_publish_claim_fields() {
        Set<String> attributes = entityManager.getMetamodel()
                .entity(BackgroundJob.class)
                .getAttributes()
                .stream()
                .map(Attribute::getName)
                .collect(Collectors.toSet());

        assertThat(attributes).contains("publishClaimId", "publishClaimUntil");
    }

    @Test
    @DisplayName("독립 스레드와 독립 트랜잭션이 경쟁해도 하나의 claim CAS만 성공한다")
    void only_one_concurrent_claim_cas_succeeds() throws Exception {
        Instant now = Instant.now();
        CountDownLatch transactionsReady = new CountDownLatch(2);
        CountDownLatch startClaim = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> claimInIndependentTransaction(
                    "claim-a", now, transactionsReady, startClaim));
            Future<Integer> second = executor.submit(() -> claimInIndependentTransaction(
                    "claim-b", now, transactionsReady, startClaim));

            assertThat(transactionsReady.await(5, TimeUnit.SECONDS))
                    .as("두 독립 트랜잭션이 CAS 시작선에 도달해야 한다")
                    .isTrue();
            startClaim.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        } finally {
            startClaim.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("CAS 경쟁 테스트 executor가 종료돼야 한다")
                    .isTrue();
        }

        BackgroundJob claimed = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(BackgroundJobStatus.PUBLISHING);
        assertThat(claimed.getPublishClaimId()).isIn("claim-a", "claim-b");
    }

    private int claimInIndependentTransaction(
            String claimId,
            Instant now,
            CountDownLatch transactionsReady,
            CountDownLatch startClaim
    ) {
        TransactionTemplate independentTransaction =
                new TransactionTemplate(transactionTemplate.getTransactionManager());
        independentTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Integer affectedRows = independentTransaction.execute(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("각 경쟁 스레드에서 독립 트랜잭션이 활성화돼야 한다")
                    .isTrue();
            transactionsReady.countDown();
            await(startClaim, "두 claim CAS의 동시 시작");
            return backgroundJobRepository.claimForPublishing(
                    job.getId(), claimId, now.plusSeconds(60), now);
        });

        return affectedRows == null ? 0 : affectedRows;
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(description + " 대기 시간이 초과됐다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " 대기 중 스레드가 중단됐다", exception);
        }
    }

    @Test
    @DisplayName("현재 claim과 다른 과거 발행자의 성공과 실패 CAS를 거절한다")
    void stale_success_and_failure_claims_are_rejected() {
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(status ->
                backgroundJobRepository.claimForPublishing(
                        job.getId(), "current", now.plusSeconds(60), now));

        int success = transactionTemplate.execute(status ->
                backgroundJobRepository.completePublish(job.getId(), "stale", now));
        int failure = transactionTemplate.execute(status ->
                backgroundJobRepository.recordPublishFailure(
                        job.getId(),
                        "stale",
                        BackgroundJobStatus.PENDING,
                        now.plusSeconds(60),
                        null,
                        "PUBLISH_FAILED",
                        "failure",
                        now));

        assertThat(success).isZero();
        assertThat(failure).isZero();
        assertThat(backgroundJobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.PUBLISHING);
    }

    @Test
    @DisplayName("후보 조회 뒤 재시도 시각이 미래가 되면 선점 CAS를 거절한다")
    void future_retry_time_rejects_claim_cas() {
        Instant now = Instant.now();
        testBackgroundJobUtils.recordPreviousPublishFailures(job, 1, now.plusSeconds(600));

        int result = transactionTemplate.execute(status ->
                backgroundJobRepository.claimForPublishing(
                        job.getId(), "claim", now.plusSeconds(60), now));

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("만료 후보의 claim이 바뀌면 과거 복구 CAS를 거절한다")
    void changed_claim_rejects_expired_recovery() {
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(status ->
                backgroundJobRepository.claimForPublishing(
                        job.getId(), "claim-a", now.minusSeconds(1), now.minusSeconds(61)));
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery("""
                        UPDATE background_job
                        SET publish_claim_id = 'claim-b'
                        WHERE id = :id
                        """)
                .setParameter("id", job.getId())
                .executeUpdate());

        int recovered = transactionTemplate.execute(status ->
                backgroundJobRepository.recoverExpiredPublish(
                        job.getId(),
                        "claim-a",
                        now,
                        BackgroundJobStatus.PENDING,
                        now.plusSeconds(60),
                        null,
                        "PUBLISH_FAILED",
                        "lease expired",
                        now));

        assertThat(recovered).isZero();
    }

    @Test
    @DisplayName("현재 claim의 발행 성공은 QUEUED로 전이하고 claim을 제거한다")
    void current_claim_completes_as_queued_and_clears_claim() {
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(status ->
                backgroundJobRepository.claimForPublishing(
                        job.getId(), "claim", now.plusSeconds(60), now));

        int completed = transactionTemplate.execute(status ->
                backgroundJobRepository.completePublish(job.getId(), "claim", now.plusSeconds(1)));

        assertThat(completed).isEqualTo(1);
        BackgroundJob queued = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(BackgroundJobStatus.QUEUED);
        assertThat(queued.getPublishClaimId()).isNull();
        assertThat(queued.getPublishClaimUntil()).isNull();
    }
}
