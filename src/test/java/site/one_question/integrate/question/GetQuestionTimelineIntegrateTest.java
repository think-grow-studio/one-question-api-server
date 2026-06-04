package site.one_question.integrate.question;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.common.HttpHeaderConstant;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("질문 타임라인 조회 통합 테스트")
class GetQuestionTimelineIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";
    private static final String TIMELINES_API = QUESTIONS_API + "/timelines";

    private Member member;
    private String token;
    private QuestionCycle cycle;

    @BeforeEach
    void setup() {
        LocalDate joinedDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(30);
        member = testMemberUtils.createSave_With_JoinedDate(joinedDate);
        token = testAuthUtils.createBearerToken(member);
        cycle = testQuestionCycleUtils.createSave_With_StartDate(member, joinedDate, TIMEZONE, 1);
    }

    @Test
    @DisplayName("연속된 기록이 있으면 baseDate를 포함해서 최신순으로 size개 조회")
    void timeline_returns_recent_records_including_base_date() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
        createDailyQuestion(today);
        createDailyQuestion(today.minusDays(1));
        createDailyQuestion(today.minusDays(2));

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", today.toString())
                        .param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(3))
                .andExpect(jsonPath("$.histories[0].date").value(today.toString()))
                .andExpect(jsonPath("$.histories[1].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.histories[2].date").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.startDate").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.endDate").value(today.toString()))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("질문이 없는 날짜는 건너뛰고 실제 존재하는 기록만 size개 조회")
    void timeline_skips_dates_without_records() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
        // 오늘, 어제 기록 후 갭(13일), 15일 전 기록
        createDailyQuestion(today);
        createDailyQuestion(today.minusDays(1));
        createDailyQuestion(today.minusDays(15));

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", today.toString())
                        .param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(3))
                .andExpect(jsonPath("$.histories[0].date").value(today.toString()))
                .andExpect(jsonPath("$.histories[1].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.histories[2].date").value(today.minusDays(15).toString()))
                .andExpect(jsonPath("$.startDate").value(today.minusDays(15).toString()))
                .andExpect(jsonPath("$.endDate").value(today.toString()))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("size보다 많은 기록이 있으면 hasPrevious가 true")
    void timeline_has_previous_when_more_records_exist() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
        createDailyQuestion(today);
        createDailyQuestion(today.minusDays(3));
        createDailyQuestion(today.minusDays(7));
        createDailyQuestion(today.minusDays(20));

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", today.toString())
                        .param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(3))
                .andExpect(jsonPath("$.histories[2].date").value(today.minusDays(7).toString()))
                .andExpect(jsonPath("$.startDate").value(today.minusDays(7).toString()))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("과거 baseDate 커서로 조회하면 그 이전 기록만 조회하고 hasNext가 true")
    void timeline_uses_past_base_date_as_cursor() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
        createDailyQuestion(today);
        createDailyQuestion(today.minusDays(5));
        createDailyQuestion(today.minusDays(8));

        LocalDate cursor = today.minusDays(5);

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", cursor.toString())
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(2))
                .andExpect(jsonPath("$.histories[0].date").value(cursor.toString()))
                .andExpect(jsonPath("$.histories[1].date").value(today.minusDays(8).toString()))
                .andExpect(jsonPath("$.startDate").value(today.minusDays(8).toString()))
                .andExpect(jsonPath("$.endDate").value(cursor.toString()))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("기록이 하나도 없으면 빈 목록과 null 날짜를 반환")
    void timeline_returns_empty_when_no_records() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", today.toString())
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(0))
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("미래 날짜 요청 시 400 Bad Request")
    void future_base_date_throws_400() throws Exception {
        LocalDate futureDate = LocalDate.now(ZoneId.of(TIMEZONE)).plusDays(1);

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", futureDate.toString())
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.FUTURE_DATE_QUESTION.getCode()));
    }

    @Test
    @DisplayName("한국보다 빠른 타임존(UTC+14)의 오늘 날짜는 서울 기준 미래여도 조회 성공")
    void timeline_allows_today_of_ahead_timezone() throws Exception {
        String aheadTimezone = "Pacific/Kiritimati"; // UTC+14, 서울보다 항상 같거나 하루 빠름
        LocalDate clientToday = LocalDate.now(ZoneId.of(aheadTimezone));
        createDailyQuestion(clientToday);

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", clientToday.toString())
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, aheadTimezone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories[0].date").value(clientToday.toString()))
                .andExpect(jsonPath("$.endDate").value(clientToday.toString()));
    }

    @Test
    @DisplayName("한국보다 느린 타임존(UTC-11) 기준 미래 날짜는 400")
    void timeline_rejects_future_date_of_behind_timezone() throws Exception {
        String behindTimezone = "Pacific/Pago_Pago"; // UTC-11, 서울보다 항상 같거나 하루 느림
        LocalDate clientFutureDate = LocalDate.now(ZoneId.of(behindTimezone)).plusDays(1);

        mockMvc.perform(get(TIMELINES_API)
                        .param("baseDate", clientFutureDate.toString())
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, behindTimezone))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.FUTURE_DATE_QUESTION.getCode()));
    }

    @Test
    @DisplayName("baseDate 없이 요청 시 400 Bad Request")
    void missing_base_date_throws_400() throws Exception {
        mockMvc.perform(get(TIMELINES_API)
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isBadRequest());
    }

    private void createDailyQuestion(LocalDate date) {
        Question question = testQuestionUtils.createSave();
        testDailyQuestionUtils.createSave_With_Date(member, cycle, question, date);
    }
}
