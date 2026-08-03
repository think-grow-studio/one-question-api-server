package site.one_question.api.analysisreport.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import site.one_question.api.analysisreport.domain.AnalysisReportType;

@Schema(description = "AI 분석 리포트 생성 요청")
public record CreateAnalysisReportRequest(
        @Schema(
                description = "리포트 타입",
                example = "THINKING_PATTERN",
                allowableValues = {"THINKING_PATTERN", "WARM_REFLECTION"}
        )
        @NotNull(message = "리포트 타입은 필수입니다.")
        AnalysisReportType reportType,

        @Schema(description = "분석할 개인 데일리 질문 답변 ID 목록 (10~15개)", example = "[1,2,3,4,5,6,7,8,9,10]")
        @NotNull(message = "분석할 답변 ID 목록은 필수입니다.")
        List<@NotNull(message = "답변 ID는 필수입니다.") @Positive(message = "답변 ID는 양수여야 합니다.") Long> dailyQuestionAnswerIds
) {
}
