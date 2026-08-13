package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportRepository;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestAnalysisReportUtils {

    private final AnalysisReportRepository repository;

    public AnalysisReport createSave(Member member) {
        return createSave(member, AnalysisReportType.THINKING_PATTERN);
    }

    public AnalysisReport createSave(Member member, AnalysisReportType reportType) {
        AnalysisReport report = AnalysisReport.createPending(member, reportType);
        return repository.save(report);
    }

    public AnalysisReport createSave_Completed(Member member, AnalysisReportType reportType) {
        AnalysisReport report = AnalysisReport.createPending(member, reportType);
        report.complete("완료된 리포트 본문", "test-provider", "test-model", "{}");
        return repository.save(report);
    }

    public AnalysisReport createSave_Failed(Member member, AnalysisReportType reportType) {
        AnalysisReport report = AnalysisReport.createPending(member, reportType);
        report.fail();
        return repository.save(report);
    }
}
