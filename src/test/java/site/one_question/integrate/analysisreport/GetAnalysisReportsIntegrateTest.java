package site.one_question.integrate.analysisreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("AI 분석 리포트 목록 조회 통합 테스트")
class GetAnalysisReportsIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Nested
    @DisplayName("목록 조회")
    class GetReportsTest {

        @Test
        @DisplayName("리포트가 없으면 빈 목록을 반환한다")
        void get_analysis_reports_when_empty_then_returns_empty_page() throws Exception {
            mockMvc.perform(get(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("본인의 PENDING, COMPLETED, FAILED 리포트를 최신순으로 반환한다")
        void get_analysis_reports_then_returns_all_own_statuses_latest_first() throws Exception {
            AnalysisReport pending = testAnalysisReportUtils.createSave(
                    member, AnalysisReportType.THINKING_PATTERN);
            AnalysisReport completed = testAnalysisReportUtils.createSave_Completed(
                    member, AnalysisReportType.WARM_REFLECTION);
            AnalysisReport failed = testAnalysisReportUtils.createSave_Failed(
                    member, AnalysisReportType.THINKING_PATTERN);

            Member otherMember = testMemberUtils.createSave();
            testAnalysisReportUtils.createSave_Completed(
                    otherMember, AnalysisReportType.WARM_REFLECTION);

            mockMvc.perform(get(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[0].analysisReportId").value(failed.getId()))
                    .andExpect(jsonPath("$.items[0].reportType").value("THINKING_PATTERN"))
                    .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.items[0].requestedAt").exists())
                    .andExpect(jsonPath("$.items[0].result").doesNotExist())
                    .andExpect(jsonPath("$.items[1].analysisReportId").value(completed.getId()))
                    .andExpect(jsonPath("$.items[1].reportType").value("WARM_REFLECTION"))
                    .andExpect(jsonPath("$.items[1].status").value("COMPLETED"))
                    .andExpect(jsonPath("$.items[1].result").doesNotExist())
                    .andExpect(jsonPath("$.items[1].provider").doesNotExist())
                    .andExpect(jsonPath("$.items[1].model").doesNotExist())
                    .andExpect(jsonPath("$.items[1].llmOptions").doesNotExist())
                    .andExpect(jsonPath("$.items[2].analysisReportId").value(pending.getId()))
                    .andExpect(jsonPath("$.items[2].status").value("PENDING"))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }
    }

    @Nested
    @DisplayName("커서 페이지네이션")
    class PaginationTest {

        @Test
        @DisplayName("여러 페이지를 순회하면 리포트가 중복이나 누락 없이 조회된다")
        void get_analysis_reports_with_cursor_then_traverses_without_duplicates_or_omissions()
                throws Exception {
            List<Long> expectedIds = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                expectedIds.add(testAnalysisReportUtils.createSave(member).getId());
            }
            expectedIds = expectedIds.reversed();

            List<Long> actualIds = new ArrayList<>();
            String cursor = null;
            boolean hasNext;

            do {
                var request = get(ANALYSIS_REPORTS_API)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("size", "3");
                if (cursor != null) {
                    request.param("cursor", cursor);
                }

                MvcResult result = mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn();
                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                body.get("items").forEach(item ->
                        actualIds.add(item.get("analysisReportId").asLong()));

                hasNext = body.get("hasNext").asBoolean();
                cursor = hasNext ? body.get("nextCursor").asText() : null;
            } while (hasNext);

            assertThat(actualIds)
                    .as("커서 순회 결과는 최신순이며 중복과 누락이 없어야 함")
                    .containsExactlyElementsOf(expectedIds);
        }

        @Test
        @DisplayName("size를 생략하면 기본 10개를 반환한다")
        void get_analysis_reports_without_size_then_returns_default_10_items()
                throws Exception {
            for (int i = 0; i < 11; i++) {
                testAnalysisReportUtils.createSave(member);
            }

            mockMvc.perform(get(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(10))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").exists());
        }

        @ParameterizedTest
        @ValueSource(strings = {"-1", "0", "51", "2147483647"})
        @DisplayName("size가 1~50 범위를 벗어나면 400을 반환한다")
        void get_analysis_reports_with_out_of_range_size_then_returns_400(String size)
                throws Exception {
            mockMvc.perform(get(ANALYSIS_REPORTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .param("size", size))
                    .andExpect(status().isBadRequest());
        }
    }
}
