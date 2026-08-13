package site.one_question.api.analysisreport.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import site.one_question.api.analysisreport.domain.AnalysisReportSource;

@Schema(description = "AI 분석 리포트 소스 스냅샷")
public record AnalysisReportSourceDto(
        @Schema(description = "질문 날짜", example = "2026-08-02")
        LocalDate questionDate,

        @Schema(description = "질문 내용")
        String questionContent,

        @Schema(description = "답변 내용")
        String answerContent
) {

    public static AnalysisReportSourceDto from(AnalysisReportSource source) {
        return new AnalysisReportSourceDto(
                source.getQuestionDate(),
                source.getQuestionContent(),
                source.getAnswerContent()
        );
    }
}
