package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;

public class AnalysisReportNotFoundForJobException extends AnalysisReportException {

    public AnalysisReportNotFoundForJobException(Long backgroundJobId) {
        super(
                AnalysisReportExceptionSpec.REPORT_NOT_FOUND_FOR_JOB,
                Map.of("backgroundJobId", backgroundJobId)
        );
    }
}
