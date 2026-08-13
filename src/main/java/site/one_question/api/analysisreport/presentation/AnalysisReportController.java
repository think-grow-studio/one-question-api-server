package site.one_question.api.analysisreport.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.api.analysisreport.application.AnalysisReportApplication;
import site.one_question.api.analysisreport.presentation.request.CreateAnalysisReportRequest;
import site.one_question.api.analysisreport.presentation.response.CreateAnalysisReportResponse;
import site.one_question.api.analysisreport.presentation.response.GetAnalysisReportsResponse;
import site.one_question.api.auth.infrastructure.annotation.PrincipalId;
import site.one_question.common.HttpHeaderConstant;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/analysis-reports")
@RequiredArgsConstructor
public class AnalysisReportController implements AnalysisReportApi {

    private final AnalysisReportApplication analysisReportApplication;

    @Override
    @GetMapping
    public ResponseEntity<GetAnalysisReportsResponse> getAll(
            @PrincipalId Long memberId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        log.info("[API] AI 분석 리포트 목록 조회 시작 - cursor: {}, size: {}", cursor, size);
        GetAnalysisReportsResponse response = analysisReportApplication.getAll(memberId, cursor, size);
        log.info("[API] AI 분석 리포트 목록 조회 종료 - itemCount: {}", response.items().size());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping
    public ResponseEntity<CreateAnalysisReportResponse> create(
            @PrincipalId Long memberId,
            @RequestHeader(value = HttpHeaderConstant.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody CreateAnalysisReportRequest request
    ) {
        log.info("[API] AI 분석 리포트 생성 요청 시작 - reportType: {}, answerCount: {}",
                request.reportType(), request.dailyQuestionAnswerIds().size());
        CreateAnalysisReportResponse response = analysisReportApplication.create(memberId, idempotencyKey, request);
        log.info("[API] AI 분석 리포트 생성 요청 종료 - jobId: {}, analysisReportId: {}",
                response.jobId(), response.analysisReportId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
