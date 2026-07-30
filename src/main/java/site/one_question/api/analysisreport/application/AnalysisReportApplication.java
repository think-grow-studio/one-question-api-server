package site.one_question.api.analysisreport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportService;
import site.one_question.api.analysisreport.domain.AnalysisReportSourceAnswerIds;
import site.one_question.api.analysisreport.domain.AnalysisReportSourceService;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerNotOwnedException;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.analysisreport.presentation.request.CreateAnalysisReportRequest;
import site.one_question.api.analysisreport.presentation.response.CreateAnalysisReportResponse;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobService;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.IdempotencyKey;
import site.one_question.api.backgroundjob.domain.RequestHash;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.DailyQuestionAnswerService;
import site.one_question.common.MdcKey;

@Service
@Transactional
@RequiredArgsConstructor
public class AnalysisReportApplication {

    /**
     * ANALYSIS_REPORT 커맨드는 도메인 행 밖의 파라미터가 없다.
     * memberId 는 {@code background_job.member_id}, reportId 는 {@code reference_id},
     * reportType 은 {@code analysis_report.report_type} 이 원천이므로 중복 저장하지 않는다.
     */
    private static final String EMPTY_PAYLOAD = "{}";

    private final MemberService memberService;
    private final DailyQuestionAnswerService dailyQuestionAnswerService;
    private final BackgroundJobService backgroundJobService;
    private final AnalysisReportService analysisReportService;
    private final AnalysisReportSourceService analysisReportSourceService;
    private final ObjectMapper objectMapper;

    public CreateAnalysisReportResponse create(
            Long memberId,
            String rawIdempotencyKey,
            CreateAnalysisReportRequest request
    ) {
        Member member = memberService.findById(memberId);
        IdempotencyKey idempotencyKey = new IdempotencyKey(rawIdempotencyKey);
        AnalysisReportType reportType = request.reportType();
        AnalysisReportSourceAnswerIds sourceIds = new AnalysisReportSourceAnswerIds(
                request.dailyQuestionAnswerIds());
        RequestHash requestHash = createRequestHash(reportType, sourceIds);

        var existingJob = backgroundJobService.findByIdempotencyKey(
                memberId, BackgroundJobType.ANALYSIS_REPORT, idempotencyKey);
        if (existingJob.isPresent()) {
            BackgroundJob backgroundJob = existingJob.get();
            backgroundJob.validateSameRequestHash(requestHash);
            AnalysisReport analysisReport =
                    analysisReportService.findById(backgroundJob.requireReferenceId());
            return CreateAnalysisReportResponse.from(backgroundJob, analysisReport);
        }

        List<DailyQuestionAnswer> sourceAnswers = dailyQuestionAnswerService.findOwnedByMemberWithQuestion(
                memberId, sourceIds.ascendingValues());
        if (sourceAnswers.size() != sourceIds.size()) {
            throw new AnalysisReportSourceAnswerNotOwnedException(memberId);
        }

        // report 를 먼저 만들어 id 를 확보한다. IDENTITY 전략이므로 save() 시점에 flush 되어
        // 같은 트랜잭션 안에서 즉시 id 를 읽을 수 있다.
        AnalysisReport analysisReport = analysisReportService.save(
                AnalysisReport.createPending(member, reportType));

        BackgroundJob backgroundJob = backgroundJobService.save(BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                analysisReport.getId(),
                EMPTY_PAYLOAD,
                resolveCorrelationId(),
                idempotencyKey,
                requestHash
        ));

        analysisReportSourceService.createAll(analysisReport, memberId, sourceAnswers);

        return CreateAnalysisReportResponse.from(backgroundJob, analysisReport);
    }

    private RequestHash createRequestHash(
            AnalysisReportType reportType,
            AnalysisReportSourceAnswerIds sourceAnswerIds
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", reportType.name());
        // 오름차순으로 정규화된 값이므로 요청 순서가 달라도 같은 해시가 나온다.
        payload.put("dailyQuestionAnswerIds", sourceAnswerIds.ascendingValues());
        return RequestHash.sha256(toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("background job payload serialization failed", e);
        }
    }

    private String resolveCorrelationId() {
        // 원 요청 문맥이 있으면 그 요청 id, 없으면(비-HTTP 생성) 새 correlation id를 만든다.
        String requestId = MDC.get(MdcKey.REQUEST_ID);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
