package site.one_question.api.analysisreport.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.analysisreport.domain.AnalysisReportJobQueueGateway;
import site.one_question.api.analysisreport.domain.AnalysisReportService;
import site.one_question.api.backgroundjob.domain.BackgroundJobMessage;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobService;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.ClaimedBackgroundJob;

@Component
public class AnalysisReportJobPublisher implements BackgroundJobPublisher {

    private final AnalysisReportService analysisReportService;
    private final BackgroundJobService backgroundJobService;
    private final AnalysisReportJobQueueGateway queueGateway;
    private final TransactionTemplate requiresNew;

    public AnalysisReportJobPublisher(
            AnalysisReportService analysisReportService,
            BackgroundJobService backgroundJobService,
            AnalysisReportJobQueueGateway queueGateway,
            PlatformTransactionManager transactionManager
    ) {
        this.analysisReportService = analysisReportService;
        this.backgroundJobService = backgroundJobService;
        this.queueGateway = queueGateway;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public BackgroundJobType supportedType() {
        return BackgroundJobType.ANALYSIS_REPORT;
    }

    @Override
    public void publish(ClaimedBackgroundJob job) {
        queueGateway.send(BackgroundJobMessage.from(job));
    }

    /**
     * 발행 재시도 소진 시 리포트도 FAILED 로 동기화한다.
     * 만료 claim 복구 경로({@code ExpiredPublishClaim})도 같은 콜백을 쓰므로 jobId 만 받고,
     * 대상 리포트는 job 의 {@code reference_id} 로 찾는다. 5회 실패 후에만 도달하는 드문
     * 경로라 조회 한 번을 감수한다.
     */
    @Override
    public void onPublishExhausted(Long jobId, Exception cause) {
        requiresNew.executeWithoutResult(status -> {
            Long reportId = backgroundJobService.findById(jobId).getReferenceId();
            if (reportId == null) {
                // ANALYSIS_REPORT job 은 항상 대상 리포트를 가리킨다. NULL 은 데이터 오염이므로
                // 어느 job 인지 알 수 있는 메시지로 실패시킨다.
                throw new IllegalStateException(
                        "ANALYSIS_REPORT job has no reference_id: " + jobId);
            }
            analysisReportService.findById(reportId).fail();
        });
    }
}
