package site.one_question.api.analysisreport.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private final AnalysisReportRepository analysisReportRepository;

    public AnalysisReport save(AnalysisReport analysisReport) {
        return analysisReportRepository.save(analysisReport);
    }

    public AnalysisReport findById(Long id) {
        return analysisReportRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "analysis report not found: " + id));
    }
}
