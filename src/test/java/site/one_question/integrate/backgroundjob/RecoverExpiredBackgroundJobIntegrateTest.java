package site.one_question.integrate.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;
import site.one_question.api.backgroundjob.application.BackgroundJobPublishApplication;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobStatus;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("만료된 백그라운드 작업 발행 claim 복구 통합 테스트")
class RecoverExpiredBackgroundJobIntegrateTest extends IntegrateTest {

    @Autowired
    private BackgroundJobPublishApplication publishApplication;

    private Member member;
    private BackgroundJob job;
    private AnalysisReport report;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        report = testAnalysisReportUtils.createSave(member);
        job = testBackgroundJobUtils.createSave_With_Reference(member, report.getId());
    }

    @Test
    @DisplayName("첫 번째 만료 claim은 PENDING으로 복구하고 백오프를 적용한다")
    void expired_claim_returns_to_pending_with_backoff() {
        claimAsExpired(job, "expired-claim");

        publishApplication.publishPendingJobs();

        BackgroundJob recovered = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getNextRetryAt()).isAfter(Instant.now());
        assertThat(recovered.getPublishClaimId()).isNull();
        assertThat(recovered.getPublishClaimUntil()).isNull();
        assertThat(analysisReportRepository.findById(report.getId()).orElseThrow().getStatus())
                .isEqualTo(AnalysisReportStatus.PENDING);
        then(analysisReportJobQueueGateway).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다섯 번째 만료 claim은 Job과 AnalysisReport를 FAILED로 전이한다")
    void fifth_expired_claim_fails_job_and_report() {
        recordPreviousFailures(job, 4);
        claimAsExpired(job, "expired-claim");

        publishApplication.publishPendingJobs();

        BackgroundJob failed = backgroundJobRepository.findById(job.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(5);
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(analysisReportRepository.findById(report.getId()).orElseThrow().getStatus())
                .isEqualTo(AnalysisReportStatus.FAILED);
    }

    @Test
    @DisplayName("최종 실패 callback이 실패해도 Job의 FAILED 상태는 유지된다")
    void callback_failure_does_not_rollback_failed_job() {
        BackgroundJob orphanJob = testBackgroundJobUtils.createSave(member);
        recordPreviousFailures(orphanJob, 4);
        claimAsExpired(orphanJob, "expired-orphan");

        publishApplication.publishPendingJobs();

        assertThat(backgroundJobRepository.findById(orphanJob.getId()).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.FAILED);
    }

    @Test
    @DisplayName("한 작업의 발행 실패는 다음 작업의 발행을 막지 않는다")
    void one_job_failure_does_not_stop_next_job() {
        AnalysisReport failingReport = testAnalysisReportUtils.createSave(member);
        BackgroundJob failing = testBackgroundJobUtils.createSave_With_Reference(
                member, failingReport.getId());
        AnalysisReport validReport = testAnalysisReportUtils.createSave(member);
        BackgroundJob valid = testBackgroundJobUtils.createSave_With_Reference(
                member, validReport.getId());

        // failing 잡의 SQS 전송만 실패시킨다 (메시지의 jobId로 특정)
        willThrow(new RuntimeException("sqs unavailable"))
                .given(analysisReportJobQueueGateway)
                .send(argThat(message -> failing.getId().equals(message.jobId())));

        publishApplication.publishPendingJobs();

        assertThat(backgroundJobRepository.findById(failing.getId()).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(backgroundJobRepository.findById(valid.getId()).orElseThrow().getStatus())
                .isEqualTo(BackgroundJobStatus.QUEUED);
    }

    private void recordPreviousFailures(BackgroundJob target, int count) {
        testBackgroundJobUtils.recordPreviousPublishFailures(
                target, count, Instant.now().minusSeconds(1));
    }

    private void claimAsExpired(BackgroundJob target, String claimId) {
        Instant now = Instant.now();
        int claimed = transactionTemplate.execute(status ->
                backgroundJobRepository.claimForPublishing(
                        target.getId(),
                        claimId,
                        now.minusSeconds(1),
                        now));
        assertThat(claimed).isEqualTo(1);
    }
}
