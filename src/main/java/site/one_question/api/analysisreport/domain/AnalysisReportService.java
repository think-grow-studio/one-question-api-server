package site.one_question.api.analysisreport.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.one_question.api.backgroundjob.domain.BackgroundJob;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private final AnalysisReportRepository analysisReportRepository;

    public AnalysisReport save(AnalysisReport analysisReport) {
        return analysisReportRepository.save(analysisReport);
    }

    public AnalysisReport findByBackgroundJob(BackgroundJob backgroundJob) {
        return analysisReportRepository.findByBackgroundJob(backgroundJob)
                .orElseThrow(() -> new IllegalStateException(
                        "analysis report missing for background job: " + backgroundJob.getId()));
    }

    public AnalysisReport findByBackgroundJobId(Long backgroundJobId) {
        return analysisReportRepository.findByBackgroundJobId(backgroundJobId)
                .orElseThrow(() -> new IllegalStateException(
                        "analysis report missing for background job: " + backgroundJobId));
    }
}
