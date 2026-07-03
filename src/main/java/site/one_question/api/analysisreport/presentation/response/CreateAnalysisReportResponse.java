package site.one_question.api.analysisreport.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.backgroundjob.domain.BackgroundJob;

@Schema(description = "AI 분석 리포트 생성 응답")
public record CreateAnalysisReportResponse(
        @Schema(description = "백그라운드 작업 ID", example = "1")
        Long jobId,

        @Schema(description = "분석 리포트 ID", example = "1")
        Long analysisReportId,

        @Schema(
                description = "리포트 타입",
                example = "THINKING_PATTERN",
                allowableValues = {"THINKING_PATTERN", "WARM_REFLECTION"}
        )
        AnalysisReportType reportType,

        @Schema(description = "작업 상태", example = "PENDING")
        String status,

        @Schema(description = "요청 시간")
        Instant requestedAt
) {

    public static CreateAnalysisReportResponse from(BackgroundJob job, AnalysisReport report) {
        return new CreateAnalysisReportResponse(
                job.getId(),
                report.getId(),
                report.getReportType(),
                job.getStatus().name(),
                job.getRequestedAt()
        );
    }
}
