package site.one_question.api.analysisreport.domain.exception;

import java.util.Map;
import site.one_question.exception.BaseException;
import site.one_question.exception.spec.ExceptionSpec;

public abstract class AnalysisReportException extends BaseException {

    protected AnalysisReportException(ExceptionSpec spec) {
        super(spec);
    }

    protected AnalysisReportException(ExceptionSpec spec, Map<String, Object> context) {
        super(spec, context);
    }
}
