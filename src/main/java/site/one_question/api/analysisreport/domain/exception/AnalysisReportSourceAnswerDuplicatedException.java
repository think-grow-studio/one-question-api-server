package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;

public class AnalysisReportSourceAnswerDuplicatedException extends AnalysisReportException {

    public AnalysisReportSourceAnswerDuplicatedException() {
        super(
                AnalysisReportExceptionSpec.SOURCE_ANSWER_DUPLICATED,
                Map.of()
        );
    }
}
