package site.one_question.api.analysisreport.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;
import site.one_question.api.analysisreport.domain.AnalysisReportType;

@Schema(description = "AI 분석 리포트 목록 아이템")
public record AnalysisReportItemDto(
        @Schema(description = "분석 리포트 ID", example = "42")
        Long analysisReportId,

        @Schema(
                description = "리포트 타입",
                example = "THINKING_PATTERN",
                allowableValues = {"THINKING_PATTERN", "WARM_REFLECTION"}
        )
        AnalysisReportType reportType,

        @Schema(
                description = "리포트 상태",
                example = "COMPLETED",
                allowableValues = {"PENDING", "COMPLETED", "FAILED"}
        )
        AnalysisReportStatus status,

        @Schema(description = "리포트 요청 시간")
        Instant requestedAt
) {

    public static AnalysisReportItemDto from(AnalysisReport report) {
        return new AnalysisReportItemDto(
                report.getId(),
                report.getReportType(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
