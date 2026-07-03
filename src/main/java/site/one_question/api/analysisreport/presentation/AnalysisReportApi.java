package site.one_question.api.analysisreport.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.one_question.api.analysisreport.presentation.request.CreateAnalysisReportRequest;
import site.one_question.api.analysisreport.presentation.response.CreateAnalysisReportResponse;

@Tag(name = "AnalysisReport", description = "AI 분석 리포트 관련 API")
public interface AnalysisReportApi {

    @Operation(
            summary = "AI 분석 리포트 생성 요청",
            description = "본인의 개인 데일리 질문 답변 10~15개를 소스로 AI 분석 리포트 백그라운드 작업을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "리포트 생성 작업 접수",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateAnalysisReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "본인의 답변이 아닌 답변 포함")
    })
    ResponseEntity<CreateAnalysisReportResponse> create(
            Long memberId,
            String idempotencyKey,
            @RequestBody(
                    description = "AI 분석 리포트 생성 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateAnalysisReportRequest.class)
                    )
            )
            CreateAnalysisReportRequest request
    );
}
