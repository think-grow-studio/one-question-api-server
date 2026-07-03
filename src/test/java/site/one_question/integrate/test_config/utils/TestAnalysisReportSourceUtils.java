package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportSource;
import site.one_question.api.analysisreport.domain.AnalysisReportSourceRepository;
import site.one_question.api.question.domain.DailyQuestionAnswer;

@Component
@RequiredArgsConstructor
public class TestAnalysisReportSourceUtils {

    private final AnalysisReportSourceRepository repository;

    public AnalysisReportSource createSave(
            AnalysisReport analysisReport,
            DailyQuestionAnswer dailyQuestionAnswer,
            int seqNo
    ) {
        AnalysisReportSource source = AnalysisReportSource.create(analysisReport, dailyQuestionAnswer, seqNo);
        return repository.save(source);
    }
}
