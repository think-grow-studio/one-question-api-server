package site.one_question.integrate.analysisreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportSource;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportExceptionSpec;
import site.one_question.api.analysisreport.presentation.request.CreateAnalysisReportRequest;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobStatus;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.exception.BackgroundJobExceptionSpec;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.common.HttpHeaderConstant;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("AI 분석 리포트 생성 통합 테스트")
class CreateAnalysisReportIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;
    private QuestionCycle cycle;
    private String idempotencyKey;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        cycle = testQuestionCycleUtils.createSave(member);
        idempotencyKey = "analysis-report-test-key";
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("본인 답변 10개로 리포트 생성 요청 시 job, report, source를 생성한다")
        void create_analysis_report_with_10_owned_answers_then_creates_job_report_sources() throws Exception {
            // given
            List<DailyQuestionAnswer> answers = createAnswers(member, cycle, 10);
            List<Long> answerIds = answers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, answerIds);

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.jobId").exists())
                    .andExpect(jsonPath("$.analysisReportId").exists())
                    .andExpect(jsonPath("$.reportType").value("THINKING_PATTERN"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.requestedAt").exists());

            // DB 검증
            assertThat(backgroundJobRepository.findAll())
                    .as("BackgroundJob가 1개 생성되어야 함")
                    .hasSize(1);
            assertThat(analysisReportRepository.findAll())
                    .as("AnalysisReport가 1개 생성되어야 함")
                    .hasSize(1);
            assertThat(analysisReportSourceRepository.findAll())
                    .as("AnalysisReportSource가 답변 수만큼 생성되어야 함")
                    .hasSize(10);

            BackgroundJob job = backgroundJobRepository.findAll().getFirst();
            AnalysisReport report = analysisReportRepository.findAll().getFirst();

            assertThat(job.getJobType())
                    .as("작업 타입은 ANALYSIS_REPORT여야 함")
                    .isEqualTo(BackgroundJobType.ANALYSIS_REPORT);
            assertThat(job.getStatus())
                    .as("초기 작업 상태는 PENDING이어야 함")
                    .isEqualTo(BackgroundJobStatus.PENDING);
            assertThat(job.getCorrelationId())
                    .as("추적 ID가 저장되어야 함")
                    .isNotBlank();
            assertThat(job.getIdempotencyKey())
                    .as("멱등키가 저장되어야 함")
                    .isEqualTo(idempotencyKey);
            assertThat(job.getMember().getId())
                    .as("작업 소유 회원 ID가 저장되어야 함")
                    .isEqualTo(member.getId());
            assertThat(job.getRequestHash())
                    .as("요청 payload hash가 저장되어야 함")
                    .hasSize(64);

            Map<String, Object> jobData = objectMapper.readValue(job.getJobData(), new TypeReference<>() {});
            assertThat(asLong(jobData.get("memberId")))
                    .as("job_data에 회원 ID가 포함되어야 함")
                    .isEqualTo(member.getId());
            assertThat(jobData.get("reportType"))
                    .as("job_data에 리포트 타입이 포함되어야 함")
                    .isEqualTo("THINKING_PATTERN");
            assertThat(jobData)
                    .as("job_data는 memberId/reportType만 담는다 — 리포트 ID는 발행자가 역조회, 소스 목록은 analysis_report_source가 원천")
                    .containsOnlyKeys("memberId", "reportType");

            assertThat(report.getReportType())
                    .as("리포트 타입이 저장되어야 함")
                    .isEqualTo(AnalysisReportType.THINKING_PATTERN);
            assertThat(report.getStatus())
                    .as("생성 직후 리포트 상태는 PENDING이어야 함")
                    .isEqualTo(AnalysisReportStatus.PENDING);
            assertThat(report.getResult())
                    .as("AI 처리 전 result는 NULL이어야 함")
                    .isNull();
            assertThat(report.getProvider())
                    .as("AI 처리 전 provider는 NULL이어야 함")
                    .isNull();
            assertThat(report.getModel())
                    .as("AI 처리 전 model은 NULL이어야 함")
                    .isNull();
            assertThat(report.getLlmOptions())
                    .as("AI 처리 전 llmOptions는 NULL이어야 함")
                    .isNull();

            List<AnalysisReportSource> sources = analysisReportSourceRepository.findAll().stream()
                    .sorted(Comparator.comparingInt(AnalysisReportSource::getSeqNo))
                    .toList();
            assertThat(sources)
                    .extracting(AnalysisReportSource::getSeqNo)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(sources)
                    .as("seq_no는 질문 날짜 내림차순 — 1번이 가장 최신 답변이어야 함")
                    .extracting(source -> source.getDailyQuestionAnswer().getId())
                    .containsExactlyElementsOf(answerIds.reversed());
            assertThat(sources.getFirst().getQuestionContent())
                    .as("소스에는 질문 내용 스냅샷이 저장되어야 함")
                    .isEqualTo("분석 질문 9");
            assertThat(sources.getFirst().getAnswerContent())
                    .as("소스에는 답변 내용 스냅샷이 저장되어야 함")
                    .isEqualTo("분석 답변 9");
        }

        @Test
        @DisplayName("같은 멱등키와 같은 요청으로 재시도하면 기존 job과 report를 반환한다")
        void create_analysis_report_with_same_idempotency_key_and_same_payload_then_returns_existing_report()
                throws Exception {
            // given
            List<DailyQuestionAnswer> answers = createAnswers(member, cycle, 10);
            List<Long> answerIds = answers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, answerIds);

            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());

            BackgroundJob job = backgroundJobRepository.findAll().getFirst();
            AnalysisReport report = analysisReportRepository.findAll().getFirst();

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.jobId").value(job.getId().intValue()))
                    .andExpect(jsonPath("$.analysisReportId").value(report.getId().intValue()))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            assertThat(backgroundJobRepository.findAll())
                    .as("같은 멱등키 재시도는 BackgroundJob를 추가 생성하지 않아야 함")
                    .hasSize(1);
            assertThat(analysisReportRepository.findAll())
                    .as("같은 멱등키 재시도는 AnalysisReport를 추가 생성하지 않아야 함")
                    .hasSize(1);
            assertThat(analysisReportSourceRepository.findAll())
                    .as("같은 멱등키 재시도는 AnalysisReportSource를 추가 생성하지 않아야 함")
                    .hasSize(10);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureTest {

        @Test
        @DisplayName("다른 회원 답변이 포함되면 403 응답하고 생성하지 않는다")
        void create_analysis_report_with_not_owned_answer_then_returns_403() throws Exception {
            // given
            List<DailyQuestionAnswer> ownedAnswers = createAnswers(member, cycle, 9);

            Member otherMember = testMemberUtils.createSave();
            QuestionCycle otherCycle = testQuestionCycleUtils.createSave(otherMember);
            DailyQuestionAnswer otherAnswer = createAnswers(otherMember, otherCycle, 1).getFirst();

            List<Long> answerIds = java.util.stream.Stream.concat(
                            ownedAnswers.stream().map(DailyQuestionAnswer::getId),
                            java.util.stream.Stream.of(otherAnswer.getId())
                    )
                    .toList();
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, answerIds);

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(AnalysisReportExceptionSpec.SOURCE_ANSWER_NOT_OWNED.getCode()));

            assertThat(backgroundJobRepository.findAll())
                    .as("소유권 검증 실패 시 BackgroundJob가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportRepository.findAll())
                    .as("소유권 검증 실패 시 AnalysisReport가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportSourceRepository.findAll())
                    .as("소유권 검증 실패 시 AnalysisReportSource가 생성되지 않아야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("중복 답변 ID가 포함되면 400 응답하고 생성하지 않는다")
        void create_analysis_report_with_duplicated_answer_id_then_returns_400() throws Exception {
            // given
            List<DailyQuestionAnswer> answers = createAnswers(member, cycle, 10);
            List<Long> answerIds = answers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            List<Long> duplicatedAnswerIds = IntStream.range(0, 10)
                    .mapToObj(index -> index == 9 ? answerIds.getFirst() : answerIds.get(index))
                    .toList();
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, duplicatedAnswerIds);

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(AnalysisReportExceptionSpec.SOURCE_ANSWER_DUPLICATED.getCode()));

            assertThat(backgroundJobRepository.findAll())
                    .as("중복 검증 실패 시 BackgroundJob가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportRepository.findAll())
                    .as("중복 검증 실패 시 AnalysisReport가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportSourceRepository.findAll())
                    .as("중복 검증 실패 시 AnalysisReportSource가 생성되지 않아야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("답변 ID가 10개 미만이면 AI-REPORT-001로 응답하고 생성하지 않는다")
        void create_analysis_report_with_less_than_10_answers_then_returns_400() throws Exception {
            // given
            List<DailyQuestionAnswer> answers = createAnswers(member, cycle, 9);
            List<Long> answerIds = answers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, answerIds);

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(
                            AnalysisReportExceptionSpec.SOURCE_ANSWER_COUNT_INVALID.getCode()));

            assertThat(backgroundJobRepository.findAll())
                    .as("답변 개수 검증 실패 시 BackgroundJob가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportRepository.findAll())
                    .as("답변 개수 검증 실패 시 AnalysisReport가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportSourceRepository.findAll())
                    .as("답변 개수 검증 실패 시 AnalysisReportSource가 생성되지 않아야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("멱등키가 없으면 400 응답하고 생성하지 않는다")
        void create_analysis_report_without_idempotency_key_then_returns_400() throws Exception {
            // given
            List<DailyQuestionAnswer> answers = createAnswers(member, cycle, 10);
            List<Long> answerIds = answers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            CreateAnalysisReportRequest request = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, answerIds);

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(
                            BackgroundJobExceptionSpec.IDEMPOTENCY_KEY_INVALID.getCode()));

            assertThat(backgroundJobRepository.findAll())
                    .as("멱등키 누락 시 BackgroundJob가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportRepository.findAll())
                    .as("멱등키 누락 시 AnalysisReport가 생성되지 않아야 함")
                    .isEmpty();
            assertThat(analysisReportSourceRepository.findAll())
                    .as("멱등키 누락 시 AnalysisReportSource가 생성되지 않아야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("같은 멱등키로 다른 요청을 보내면 409 응답하고 추가 생성하지 않는다")
        void create_analysis_report_with_same_idempotency_key_and_different_payload_then_returns_409()
                throws Exception {
            // given
            List<DailyQuestionAnswer> firstAnswers = createAnswers(member, cycle, 10);
            List<Long> firstAnswerIds = firstAnswers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            CreateAnalysisReportRequest firstRequest = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, firstAnswerIds);

            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstRequest)))
                    .andExpect(status().isAccepted());

            List<DailyQuestionAnswer> secondAnswers = createAnswers(
                    member, cycle, 10, LocalDate.of(2026, 2, 1));
            List<Long> secondAnswerIds = secondAnswers.stream()
                    .map(DailyQuestionAnswer::getId)
                    .toList();
            CreateAnalysisReportRequest secondRequest = new CreateAnalysisReportRequest(
                    AnalysisReportType.THINKING_PATTERN, secondAnswerIds);

            // when & then
            mockMvc.perform(post(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.IDEMPOTENCY_KEY, idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(
                            BackgroundJobExceptionSpec.REQUEST_HASH_CONFLICT.getCode()));

            assertThat(backgroundJobRepository.findAll())
                    .as("멱등키 충돌 시 BackgroundJob를 추가 생성하지 않아야 함")
                    .hasSize(1);
            assertThat(analysisReportRepository.findAll())
                    .as("멱등키 충돌 시 AnalysisReport를 추가 생성하지 않아야 함")
                    .hasSize(1);
            assertThat(analysisReportSourceRepository.findAll())
                    .as("멱등키 충돌 시 AnalysisReportSource를 추가 생성하지 않아야 함")
                    .hasSize(10);
        }
    }

    private List<DailyQuestionAnswer> createAnswers(Member owner, QuestionCycle ownerCycle, int count) {
        return createAnswers(owner, ownerCycle, count, LocalDate.of(2026, 1, 1));
    }

    private List<DailyQuestionAnswer> createAnswers(
            Member owner,
            QuestionCycle ownerCycle,
            int count,
            LocalDate startDate
    ) {
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    Question question = testQuestionUtils.createSave_With_Content("분석 질문 " + index);
                    DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(
                            owner, ownerCycle, question, startDate.plusDays(index));
                    return testDailyQuestionAnswerUtils.createSave_With_Content(
                            dailyQuestion, owner, "분석 답변 " + index);
                })
                .toList();
    }

    private long asLong(Object value) {
        return ((Number) value).longValue();
    }
}
