package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;

public class AnalysisReportCompletionDataInvalidException extends AnalysisReportException {

    public AnalysisReportCompletionDataInvalidException(Long analysisReportId) {
        super(
                AnalysisReportExceptionSpec.COMPLETION_DATA_INVALID,
                Map.of("analysisReportId", String.valueOf(analysisReportId))
        );
    }
}
