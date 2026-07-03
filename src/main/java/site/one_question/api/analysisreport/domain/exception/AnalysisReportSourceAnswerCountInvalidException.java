package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;

public class AnalysisReportSourceAnswerCountInvalidException extends AnalysisReportException {

    public AnalysisReportSourceAnswerCountInvalidException(int count) {
        super(
                AnalysisReportExceptionSpec.SOURCE_ANSWER_COUNT_INVALID,
                Map.of("count", count)
        );
    }
}
