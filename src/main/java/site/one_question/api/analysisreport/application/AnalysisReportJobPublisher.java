package site.one_question.api.analysisreport.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.analysisreport.domain.AnalysisReportJobQueueGateway;
import site.one_question.api.analysisreport.domain.AnalysisReportService;
import site.one_question.api.backgroundjob.domain.BackgroundJobMessage;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.ClaimedBackgroundJob;

@Component
public class AnalysisReportJobPublisher implements BackgroundJobPublisher {

    private final AnalysisReportService analysisReportService;
    private final AnalysisReportJobQueueGateway queueGateway;
    private final TransactionTemplate requiresNew;

    public AnalysisReportJobPublisher(
            AnalysisReportService analysisReportService,
            AnalysisReportJobQueueGateway queueGateway,
            PlatformTransactionManager transactionManager
    ) {
        this.analysisReportService = analysisReportService;
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

    @Override
    public void onPublishExhausted(Long jobId, Exception cause) {
        requiresNew.executeWithoutResult(status ->
                analysisReportService.findByBackgroundJobId(jobId).fail());
    }
}
