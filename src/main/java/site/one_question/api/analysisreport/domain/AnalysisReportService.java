package site.one_question.api.analysisreport.domain;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportNotFoundException;
import site.one_question.api.member.domain.Member;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private final AnalysisReportRepository analysisReportRepository;

    /** PENDING 리포트를 만들어 저장한다. 반환 시점에 id 가 확정된다(IDENTITY 전략). */
    public AnalysisReport createPending(Member member, AnalysisReportType reportType) {
        return analysisReportRepository.save(AnalysisReport.createPending(member, reportType));
    }

    public AnalysisReport findById(Long id) {
        return analysisReportRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "analysis report not found: " + id));
    }

    public AnalysisReport findOwnedById(Long id, Long memberId) {
        return analysisReportRepository.findWithSourcesByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new AnalysisReportNotFoundException(id));
    }

    public List<AnalysisReport> findMemberReports(
            Long memberId,
            Long lastSeenReportId,
            int limit
    ) {
        return analysisReportRepository.findAllByMemberIdBeforeCursor(
                memberId, lastSeenReportId, PageRequest.of(0, limit));
    }
}
