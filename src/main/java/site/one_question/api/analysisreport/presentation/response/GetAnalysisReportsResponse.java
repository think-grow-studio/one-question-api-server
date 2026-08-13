package site.one_question.api.analysisreport.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 분석 리포트 목록 응답")
public record GetAnalysisReportsResponse(
        @Schema(description = "분석 리포트 목록")
        List<AnalysisReportItemDto> items,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 커서 (다음 요청 시 cursor 파라미터로 사용)", example = "21")
        Long nextCursor
) {
}
