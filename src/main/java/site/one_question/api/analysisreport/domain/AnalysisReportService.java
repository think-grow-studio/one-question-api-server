package site.one_question.api.analysisreport.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportNotFoundForJobException;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private final AnalysisReportRepository analysisReportRepository;

    public AnalysisReport save(AnalysisReport analysisReport) {
        return analysisReportRepository.save(analysisReport);
    }

    public AnalysisReport findByBackgroundJobOrThrow(BackgroundJob backgroundJob) {
        return analysisReportRepository.findByBackgroundJob(backgroundJob)
                .orElseThrow(() -> new AnalysisReportNotFoundForJobException(backgroundJob.getId()));
    }
}
