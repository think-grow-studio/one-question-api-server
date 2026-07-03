package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;

public class AnalysisReportSourceAnswerNotOwnedException extends AnalysisReportException {

    public AnalysisReportSourceAnswerNotOwnedException(Long memberId) {
        super(
                AnalysisReportExceptionSpec.SOURCE_ANSWER_NOT_OWNED,
                Map.of("memberId", memberId)
        );
    }
}
