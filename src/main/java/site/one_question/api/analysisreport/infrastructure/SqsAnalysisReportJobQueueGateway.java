package site.one_question.api.analysisreport.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import site.one_question.api.analysisreport.domain.AnalysisReportJobQueueGateway;
import site.one_question.api.backgroundjob.domain.BackgroundJobMessage;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class SqsAnalysisReportJobQueueGateway implements AnalysisReportJobQueueGateway {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsAnalysisReportJobQueueGateway(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${app.analysis-report.sqs.queue-url:}") String queueUrl
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void send(BackgroundJobMessage message) {
        if (queueUrl.isBlank()) {
            throw new IllegalStateException(
                    "분석 리포트 SQS 큐 URL이 설정되지 않았습니다 (ANALYSIS_REPORT_QUEUE_URL)");
        }
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(serialize(message))
                .build());
    }

    private String serialize(BackgroundJobMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("BackgroundJob 발행 메시지 직렬화에 실패했습니다", e);
        }
    }
}
