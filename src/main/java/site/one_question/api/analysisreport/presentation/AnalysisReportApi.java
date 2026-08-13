package site.one_question.api.analysisreport.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import site.one_question.api.analysisreport.presentation.request.CreateAnalysisReportRequest;
import site.one_question.api.analysisreport.presentation.response.CreateAnalysisReportResponse;
import site.one_question.api.analysisreport.presentation.response.GetAnalysisReportDetailResponse;
import site.one_question.api.analysisreport.presentation.response.GetAnalysisReportsResponse;
import site.one_question.common.HttpHeaderConstant;

@Tag(name = "AnalysisReport", description = "AI 분석 리포트 관련 API")
public interface AnalysisReportApi {

    @Operation(
            summary = "AI 분석 리포트 상세 조회",
            description = "본인의 분석 리포트 결과와 생성에 사용한 소스 스냅샷을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리포트 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = GetAnalysisReportDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "리포트를 찾을 수 없음 (AI-REPORT-006)")
    })
    ResponseEntity<GetAnalysisReportDetailResponse> getDetail(
            Long memberId,
            Long analysisReportId
    );

    @Operation(
            summary = "AI 분석 리포트 목록 조회",
            description = "본인의 PENDING, COMPLETED, FAILED 리포트를 최신순으로 커서 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "리포트 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = GetAnalysisReportsResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ResponseEntity<GetAnalysisReportsResponse> getAll(
            Long memberId,
            @Parameter(name = "cursor", description = "이전 응답의 nextCursor", in = ParameterIn.QUERY)
            Long cursor,
            @Parameter(
                    name = "size",
                    description = "페이지 크기 (1~50)",
                    example = "10",
                    in = ParameterIn.QUERY,
                    schema = @Schema(minimum = "1", maximum = "50")
            )
            @Min(1) @Max(50)
            Integer size
    );

    @Operation(
            summary = "AI 분석 리포트 생성 요청",
            description = "본인의 개인 데일리 질문 답변 10~15개를 소스로 AI 분석 리포트 백그라운드 작업을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "리포트 생성 작업 접수",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateAnalysisReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (멱등키 누락·형식 오류, 답변 개수/중복 위반)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "본인의 답변이 아닌 답변 포함"),
            @ApiResponse(responseCode = "409", description = "같은 멱등키로 다른 요청 내용 전송 (BACKGROUND-JOB-003)")
    })
    ResponseEntity<CreateAnalysisReportResponse> create(
            Long memberId,

            @Parameter(
                    name = HttpHeaderConstant.IDEMPOTENCY_KEY,
                    in = ParameterIn.HEADER,
                    required = true,
                    description = """
                            생성 요청마다 클라이언트가 부여하는 멱등키. 같은 생성 의도를 재시도할 때만 같은 키를 재사용한다.
                            같은 키 + 같은 요청 내용이면 기존 작업/리포트로 수렴하고, 같은 키 + 다른 내용이면 409로 거절한다.
                            누락 시 400 (BACKGROUND-JOB-001).
                            """,
                    example = "9f1c2b7e-3d4a-4f8b-9c2e-5a6b7c8d9e0f"
            )
            String idempotencyKey,
            @RequestBody(
                    description = "AI 분석 리포트 생성 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateAnalysisReportRequest.class)
                    )
            )
            @Valid
            CreateAnalysisReportRequest request
    );
}
