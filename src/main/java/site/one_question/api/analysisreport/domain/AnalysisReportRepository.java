package site.one_question.api.analysisreport.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.one_question.api.backgroundjob.domain.BackgroundJob;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    Optional<AnalysisReport> findByBackgroundJob(BackgroundJob backgroundJob);

    Optional<AnalysisReport> findByBackgroundJobId(Long backgroundJobId);
}
