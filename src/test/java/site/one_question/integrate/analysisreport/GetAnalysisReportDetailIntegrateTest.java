package site.one_question.integrate.analysisreport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("AI 분석 리포트 상세 조회 통합 테스트")
class GetAnalysisReportDetailIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;
    private QuestionCycle cycle;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        cycle = testQuestionCycleUtils.createSave(member);
    }

    @Nested
    @DisplayName("상세 조회 성공")
    class SuccessTest {

        @Test
        @DisplayName("COMPLETED 리포트는 result content와 source 스냅샷을 seqNo 순으로 반환한다")
        void get_completed_report_then_returns_result_content_and_ordered_sources()
                throws Exception {
            AnalysisReport report = testAnalysisReportUtils.createSave_Completed(
                    member,
                    AnalysisReportType.WARM_REFLECTION,
                    "{\"content\":\"분석 결과입니다.\\n두 번째 문장입니다.\"}"
            );
            DailyQuestionAnswer olderAnswer = createAnswer(
                    LocalDate.of(2026, 8, 1), "오래된 질문", "오래된 답변");
            DailyQuestionAnswer newerAnswer = createAnswer(
                    LocalDate.of(2026, 8, 2), "최신 질문", "최신 답변");
            testAnalysisReportSourceUtils.createSave(report, olderAnswer, 2);
            testAnalysisReportSourceUtils.createSave(report, newerAnswer, 1);

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", report.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analysisReportId").value(report.getId()))
                    .andExpect(jsonPath("$.reportType").value("WARM_REFLECTION"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.result").value("분석 결과입니다.\n두 번째 문장입니다."))
                    .andExpect(jsonPath("$.requestedAt").exists())
                    .andExpect(jsonPath("$.sources.length()").value(2))
                    .andExpect(jsonPath("$.sources[0].questionDate").value("2026-08-02"))
                    .andExpect(jsonPath("$.sources[0].questionContent").value("최신 질문"))
                    .andExpect(jsonPath("$.sources[0].answerContent").value("최신 답변"))
                    .andExpect(jsonPath("$.sources[0].id").doesNotExist())
                    .andExpect(jsonPath("$.sources[0].analysisReportId").doesNotExist())
                    .andExpect(jsonPath("$.sources[0].dailyQuestionAnswerId").doesNotExist())
                    .andExpect(jsonPath("$.sources[1].questionDate").value("2026-08-01"))
                    .andExpect(jsonPath("$.sources[1].questionContent").value("오래된 질문"))
                    .andExpect(jsonPath("$.sources[1].answerContent").value("오래된 답변"))
                    .andExpect(jsonPath("$.provider").doesNotExist())
                    .andExpect(jsonPath("$.model").doesNotExist())
                    .andExpect(jsonPath("$.llmOptions").doesNotExist());
        }

        @ParameterizedTest
        @MethodSource("invalidResults")
        @DisplayName("COMPLETED 리포트의 result 형식이 올바르지 않으면 기본 오류 문구를 반환한다")
        void get_completed_report_with_invalid_result_then_returns_fallback(String result)
                throws Exception {
            AnalysisReport report = testAnalysisReportUtils.createSave_Completed(
                    member, AnalysisReportType.THINKING_PATTERN, result);

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", report.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("오류가 발생했습니다."))
                    .andExpect(jsonPath("$.sources.length()").value(0));
        }

        static Stream<Arguments> invalidResults() {
            return Stream.of(
                    Arguments.of("not-json"),
                    Arguments.of("{}"),
                    Arguments.of("{\"content\":\" \"}"),
                    Arguments.of("{\"content\":123}"),
                    Arguments.of("{\"content\":\"정상처럼 보임\"} garbage"),
                    Arguments.of("{\"content\":\"첫 번째\"} {\"content\":\"두 번째\"}")
            );
        }

        @Test
        @DisplayName("잘못된 result의 기본 오류 문구는 회원 locale로 반환한다")
        void get_completed_report_with_invalid_result_then_returns_member_localized_fallback()
                throws Exception {
            Member englishMember = testMemberUtils.createSave_With_Locale("en-US");
            String englishMemberToken = testAuthUtils.createBearerToken(englishMember);
            AnalysisReport report = testAnalysisReportUtils.createSave_Completed(
                    englishMember, AnalysisReportType.THINKING_PATTERN, "not-json");

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", report.getId())
                            .header(HttpHeaders.AUTHORIZATION, englishMemberToken)
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("An error occurred."));
        }

        @Test
        @DisplayName("회원 locale이 비표준이면 한국어 오류 문구로 fallback한다")
        void get_completed_report_with_nonstandard_member_locale_then_returns_korean_fallback()
                throws Exception {
            Member nonstandardLocaleMember = testMemberUtils.createSave_With_Locale("KR-kr");
            String nonstandardLocaleMemberToken = testAuthUtils.createBearerToken(
                    nonstandardLocaleMember);
            AnalysisReport report = testAnalysisReportUtils.createSave_Completed(
                    nonstandardLocaleMember, AnalysisReportType.THINKING_PATTERN, "not-json");

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", report.getId())
                            .header(HttpHeaders.AUTHORIZATION, nonstandardLocaleMemberToken)
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("오류가 발생했습니다."));
        }

        @Test
        @DisplayName("COMPLETED 리포트의 저장 result가 NULL이면 기본 오류 문구를 반환한다")
        void get_completed_report_with_null_result_then_returns_fallback() throws Exception {
            AnalysisReport report = testAnalysisReportUtils.createSave_Completed(
                    member, AnalysisReportType.THINKING_PATTERN);
            transactionTemplate.executeWithoutResult(status -> {
                entityManager.createNativeQuery(
                                "UPDATE analysis_report SET result = NULL WHERE id = :id")
                        .setParameter("id", report.getId())
                        .executeUpdate();
                entityManager.clear();
            });

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", report.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("오류가 발생했습니다."));
        }

        @Test
        @DisplayName("PENDING과 FAILED 리포트는 result를 NULL로 반환한다")
        void get_uncompleted_report_then_returns_null_result() throws Exception {
            AnalysisReport pending = testAnalysisReportUtils.createSave(member);
            AnalysisReport failed = testAnalysisReportUtils.createSave_Failed(
                    member, AnalysisReportType.THINKING_PATTERN);

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", pending.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.result").doesNotExist());

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", failed.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.result").doesNotExist());
        }
    }

    @Nested
    @DisplayName("상세 조회 실패")
    class FailureTest {

        @Test
        @DisplayName("다른 회원 리포트는 404를 반환한다")
        void get_other_members_report_then_returns_404() throws Exception {
            Member otherMember = testMemberUtils.createSave();
            AnalysisReport otherReport = testAnalysisReportUtils.createSave(otherMember);

            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", otherReport.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI-REPORT-006"));
        }

        @Test
        @DisplayName("존재하지 않는 리포트는 404를 반환한다")
        void get_missing_report_then_returns_404() throws Exception {
            mockMvc.perform(get(ANALYSIS_REPORTS_API + "/{id}", Long.MAX_VALUE)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AI-REPORT-006"));
        }
    }

    private DailyQuestionAnswer createAnswer(
            LocalDate date,
            String questionContent,
            String answerContent
    ) {
        Question question = testQuestionUtils.createSave_With_Content(questionContent);
        DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(
                member, cycle, question, date);
        return testDailyQuestionAnswerUtils.createSave_With_Content(
                dailyQuestion, member, answerContent);
    }
}
