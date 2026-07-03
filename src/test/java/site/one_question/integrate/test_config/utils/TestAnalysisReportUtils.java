package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportRepository;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestAnalysisReportUtils {

    private final AnalysisReportRepository repository;

    public AnalysisReport createSave(BackgroundJob backgroundJob, Member member) {
        AnalysisReport report = AnalysisReport.createPending(
                backgroundJob,
                member,
                AnalysisReportType.THINKING_PATTERN
        );
        return repository.save(report);
    }
}
