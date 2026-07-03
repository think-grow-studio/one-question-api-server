package site.one_question.api.analysisreport.domain;

import java.util.List;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerCountInvalidException;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerDuplicatedException;

public record AnalysisReportSourceAnswerIds(List<Long> values) {

    private static final int MIN_COUNT = 10;
    private static final int MAX_COUNT = 15;

    public AnalysisReportSourceAnswerIds {
        if (values == null) {
            throw new AnalysisReportSourceAnswerCountInvalidException(0);
        }

        List<Long> distinctValues = values.stream().distinct().toList();
        if (distinctValues.size() != values.size()) {
            throw new AnalysisReportSourceAnswerDuplicatedException();
        }

        int count = distinctValues.size();
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new AnalysisReportSourceAnswerCountInvalidException(count);
        }

        values = List.copyOf(values);
    }
}
