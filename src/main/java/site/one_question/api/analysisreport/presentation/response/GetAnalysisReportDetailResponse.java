package site.one_question.api.analysisreport.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;
import site.one_question.api.analysisreport.domain.AnalysisReportType;

@Schema(description = "AI 분석 리포트 상세 응답")
public record GetAnalysisReportDetailResponse(
        @Schema(description = "분석 리포트 ID", example = "42")
        Long analysisReportId,

        @Schema(description = "리포트 타입", example = "THINKING_PATTERN")
        AnalysisReportType reportType,

        @Schema(description = "리포트 상태", example = "COMPLETED")
        AnalysisReportStatus status,

        @Schema(description = "리포트 결과 본문. 완료되지 않았으면 NULL")
        String result,

        @Schema(description = "리포트 생성에 사용한 소스 스냅샷")
        List<AnalysisReportSourceDto> sources,

        @Schema(description = "리포트 요청 시간")
        Instant requestedAt
) {

    public static GetAnalysisReportDetailResponse from(
            AnalysisReport report,
            String result
    ) {
        List<AnalysisReportSourceDto> sources = report.getSources().stream()
                .map(AnalysisReportSourceDto::from)
                .toList();

        return new GetAnalysisReportDetailResponse(
                report.getId(),
                report.getReportType(),
                report.getStatus(),
                result,
                sources,
                report.getCreatedAt()
        );
    }
}
