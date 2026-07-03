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
            AnalysisReport analysisReport = analysisReportService.findByBackgroundJob(backgroundJob);
            return CreateAnalysisReportResponse.from(backgroundJob, analysisReport);
        }

        List<DailyQuestionAnswer> sourceAnswers = dailyQuestionAnswerService.findOwnedByMemberWithQuestion(
                memberId, sourceIds.values());
        if (sourceAnswers.size() != sourceIds.values().size()) {
            throw new AnalysisReportSourceAnswerNotOwnedException(memberId);
        }

        BackgroundJob backgroundJob = backgroundJobService.save(BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                createJobData(memberId, reportType, sourceIds),
                getTraceId(),
                idempotencyKey,
                requestHash
        ));
        AnalysisReport analysisReport = analysisReportService.save(
                AnalysisReport.createPending(backgroundJob, member, reportType));

        analysisReportSourceService.createAll(analysisReport, memberId, sourceAnswers);

        return CreateAnalysisReportResponse.from(backgroundJob, analysisReport);
    }

    private String createJobData(
            Long memberId,
            AnalysisReportType reportType,
            AnalysisReportSourceAnswerIds sourceAnswerIds
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", memberId);
        payload.put("reportType", reportType.name());
        payload.put("dailyQuestionAnswerIds", sourceAnswerIds.values());
        return toJson(payload);
    }

    private RequestHash createRequestHash(
            AnalysisReportType reportType,
            AnalysisReportSourceAnswerIds sourceAnswerIds
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", reportType.name());
        payload.put("dailyQuestionAnswerIds", sourceAnswerIds.values());
        return RequestHash.sha256(toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("background job payload serialization failed", e);
        }
    }

    private String getTraceId() {
        String traceId = MDC.get(MdcKey.REQUEST_ID);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
