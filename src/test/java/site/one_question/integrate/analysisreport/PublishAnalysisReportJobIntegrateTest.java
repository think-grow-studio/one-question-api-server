package site.one_question.integrate.analysisreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.one_question.api.backgroundjob.application.BackgroundJobPublishApplication;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobMessage;
import site.one_question.api.backgroundjob.domain.BackgroundJobStatus;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("AI 분석 리포트 작업 SQS 발행 통합 테스트")
class PublishAnalysisReportJobIntegrateTest extends IntegrateTest {

    @Autowired
    private BackgroundJobPublishApplication publishApplication;

    private Member member;
    private BackgroundJob job;
    private AnalysisReport report;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        report = testAnalysisReportUtils.createSave(member);
        job = testBackgroundJobUtils.createSave_With_Reference(member, report.getId());
    }

    @Nested
    @DisplayName("발행 성공")
    class SuccessTest {

        @Test
        @DisplayName("PENDING 작업을 발행하면 메시지에 식별자를 조립하고 QUEUED로 전이한다")
        void publish_pending_job_then_sends_assembled_message_and_enqueues() throws Exception {
            // given
            willAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("SQS 외부 호출 중에는 DB 트랜잭션이 없어야 함")
                        .isFalse();
                return null;
            }).given(analysisReportJobQueueGateway).send(any());

            // when
            publishApplication.publishPendingJobs();

            // then
            ArgumentCaptor<BackgroundJobMessage> messageCaptor =
                    ArgumentCaptor.forClass(BackgroundJobMessage.class);
            then(analysisReportJobQueueGateway).should().send(messageCaptor.capture());

            // Claim Check — 메시지 타입이 jobId·correlationId만 갖도록 강제(그 외 식별자/페이로드 불가)
            BackgroundJobMessage message = messageCaptor.getValue();
            assertThat(message.jobId())
                    .as("메시지에 작업 ID가 포함되어야 함")
                    .isEqualTo(job.getId());
            assertThat(message.correlationId())
                    .as("메시지에 correlation ID가 포함되어야 함")
                    .isEqualTo(job.getCorrelationId());

            BackgroundJob published = backgroundJobRepository.findById(job.getId()).orElseThrow();
            assertThat(published.getStatus())
                    .as("발행 성공한 작업은 QUEUED로 전이되어야 함")
                    .isEqualTo(BackgroundJobStatus.QUEUED);
        }

        @Test
        @DisplayName("재시도 시각이 도래하지 않은 작업은 발행하지 않는다")
        void publish_when_next_retry_not_due_then_skips_job() {
            // given
            testBackgroundJobUtils.recordPreviousPublishFailures(
                    job, 1, Instant.now().plusSeconds(600));

            // when
            publishApplication.publishPendingJobs();

            // then
            then(analysisReportJobQueueGateway).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("발행 실패")
    class FailureTest {

        @Test
        @DisplayName("발행 실패 시 재시도 정보를 기록하고 PENDING을 유지한다")
        void publish_fails_then_records_retry_and_stays_pending() {
            // given
            willThrow(new RuntimeException("sqs unavailable"))
                    .given(analysisReportJobQueueGateway).send(any());

            // when
            publishApplication.publishPendingJobs();

            // then
            BackgroundJob failed = backgroundJobRepository.findById(job.getId()).orElseThrow();
            assertThat(failed.getStatus())
                    .as("재시도 여지가 있으면 PENDING을 유지해야 함")
                    .isEqualTo(BackgroundJobStatus.PENDING);
            assertThat(failed.getRetryCount())
                    .as("재시도 횟수가 증가해야 함")
                    .isEqualTo(1);
            assertThat(failed.getNextRetryAt())
                    .as("다음 재시도 시각이 미래로 예약되어야 함")
                    .isAfter(Instant.now());
            assertThat(failed.getErrorCode())
                    .as("발행 실패 에러 코드가 기록되어야 함")
                    .isEqualTo("PUBLISH_FAILED");

            AnalysisReport pendingReport = analysisReportRepository.findById(report.getId()).orElseThrow();
            assertThat(pendingReport.getStatus())
                    .as("재시도 중에는 리포트가 PENDING을 유지해야 함")
                    .isEqualTo(AnalysisReportStatus.PENDING);
        }

        @Test
        @DisplayName("재시도 소진 시 작업과 리포트를 함께 FAILED로 전이한다")
        void publish_fails_after_max_attempts_then_fails_job_and_report() {
            // given: 4회 실패 이력 (다음 시도가 5회째 = 마지막)
            testBackgroundJobUtils.recordPreviousPublishFailures(
                    job, 4, Instant.now().minusSeconds(1));
            willThrow(new RuntimeException("sqs unavailable"))
                    .given(analysisReportJobQueueGateway).send(any());

            // when
            publishApplication.publishPendingJobs();

            // then
            BackgroundJob failed = backgroundJobRepository.findById(job.getId()).orElseThrow();
            assertThat(failed.getStatus())
                    .as("재시도 소진 시 작업은 FAILED여야 함")
                    .isEqualTo(BackgroundJobStatus.FAILED);
            assertThat(failed.getFinishedAt())
                    .as("종료 시각이 기록되어야 함")
                    .isNotNull();

            AnalysisReport failedReport = analysisReportRepository.findById(report.getId()).orElseThrow();
            assertThat(failedReport.getStatus())
                    .as("작업 최종 실패 시 리포트도 FAILED로 동기화되어야 함")
                    .isEqualTo(AnalysisReportStatus.FAILED);
        }
    }

}
