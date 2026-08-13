package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;

public class AnalysisReportNotFoundException extends AnalysisReportException {

    public AnalysisReportNotFoundException(Long analysisReportId) {
        super(
                AnalysisReportExceptionSpec.REPORT_NOT_FOUND,
                Map.of("analysisReportId", analysisReportId)
        );
    }
}
