package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;

public class AnalysisReportNotPendingException extends AnalysisReportException {

    public AnalysisReportNotPendingException(Long analysisReportId, AnalysisReportStatus status) {
        super(
                AnalysisReportExceptionSpec.REPORT_NOT_PENDING,
                Map.of(
                        "analysisReportId", String.valueOf(analysisReportId),
                        "status", status.name()
                )
        );
    }
}
