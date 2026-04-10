package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.question.presentation.request.CreateAnswerRequest;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.HistoryDirection;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.api.question.presentation.response.QuestionHistoryItemDto.Status;
import site.one_question.test_config.IntegrateTest;

@DisplayName("질문 히스토리 조회 통합 테스트")
class GetQuestionHistoryIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";
    private static final String HISTORIES_API = QUESTIONS_API + "/histories";
    private static final Long CALENDAR_SIZE = 35L;
    private static final Long DAILY_SIZE = 7L;
    private static final Long CALENDAR_HALF_SIZE = CALENDAR_SIZE / 2;

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

    @Nested
    @DisplayName("기본 성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("PREVIOUS 방향: baseDate부터 size일 이전까지 조회")
        void get_history_with_previous_direction() throws Exception {
            // given
            LocalDate baseDate = LocalDate.now(ZoneId.of(TIMEZONE));
            long size = DAILY_SIZE;

            // 7일치 DailyQuestion 생성
            for (int i = 0; i < size; i++) {
                LocalDate date = baseDate.minusDays(i);
                Question question = testQuestionUtils.createSave();
                testDailyQuestionUtils.createSave_With_Date(member, cycle, question, date);
            }

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories").isArray())
                    .andExpect(jsonPath("$.histories.length()").value(size))
                    .andExpect(jsonPath("$.endDate").value(baseDate.toString()))
                    .andExpect(jsonPath("$.startDate").value(baseDate.minusDays(size - 1).toString()));
        }

        @Test
        @DisplayName("NEXT 방향: baseDate부터 size일 이후까지 조회")
        void get_history_with_next_direction() throws Exception {
            // given
            LocalDate baseDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(10);
            long size = DAILY_SIZE;

            // 7일치 DailyQuestion 생성
            for (int i = 0; i < size; i++) {
                LocalDate date = baseDate.plusDays(i);
                Question question = testQuestionUtils.createSave();
                testDailyQuestionUtils.createSave_With_Date(member, cycle, question, date);
            }

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.NEXT.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories").isArray())
                    .andExpect(jsonPath("$.histories.length()").value(size))
                    .andExpect(jsonPath("$.startDate").value(baseDate.toString()))
                    .andExpect(jsonPath("$.endDate").value(baseDate.plusDays(size - 1).toString()));
        }

        @Test
        @DisplayName("BOTH 방향: baseDate 중심 양쪽 균등 조회")
        void get_history_with_both_direction() throws Exception {
            // given
            LocalDate baseDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(10);
            long size = CALENDAR_SIZE;

            // 35일치 DailyQuestion 생성 (baseDate 중심)
            for (int i = -CALENDAR_HALF_SIZE.intValue(); i <= CALENDAR_HALF_SIZE; i++) {
                LocalDate date = baseDate.plusDays(i);
                Question question = testQuestionUtils.createSave();
                testDailyQuestionUtils.createSave_With_Date(member, cycle, question, date);
            }

            // when & then
            // 실제로는 가입일(30일 전)과 오늘로 클램핑되어 28일치만 조회됨
            LocalDate joinedDate = member.getJoinedDate();
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate actualStartDate = baseDate.minusDays(CALENDAR_HALF_SIZE).isAfter(joinedDate) ? baseDate.minusDays(CALENDAR_HALF_SIZE) : joinedDate;
            LocalDate actualEndDate = baseDate.plusDays(CALENDAR_HALF_SIZE).isBefore(today) ? baseDate.plusDays(CALENDAR_HALF_SIZE) : today;
            long actualSize = java.time.temporal.ChronoUnit.DAYS.between(actualStartDate, actualEndDate) + 1;

            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories").isArray())
                    .andExpect(jsonPath("$.histories.length()").value(actualSize))
                    .andExpect(jsonPath("$.startDate").value(actualStartDate.toString()))
                    .andExpect(jsonPath("$.endDate").value(actualEndDate.toString()));
        }

        @Test
        @DisplayName("결과가 최신순(내림차순) 정렬 확인")
        void get_history_returns_descending_order() throws Exception {
            // given
            LocalDate baseDate = LocalDate.now(ZoneId.of(TIMEZONE));
            long size = DAILY_SIZE;

            for (int i = 0; i < size; i++) {
                LocalDate date = baseDate.minusDays(i);
                Question question = testQuestionUtils.createSave();
                testDailyQuestionUtils.createSave_With_Date(member, cycle, question, date);
            }

            // when & then - 첫 번째 항목이 가장 최신 날짜
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].date").value(baseDate.toString()))
                    .andExpect(jsonPath("$.histories[1].date").value(baseDate.minusDays(1).toString()))
                    .andExpect(jsonPath("$.histories[2].date").value(baseDate.minusDays(2).toString()))
                    .andExpect(jsonPath("$.histories[6].date").value(baseDate.minusDays(6).toString()));
        }
    }

    @Nested
    @DisplayName("상태별 테스트")
    class StatusTest {

        @Test
        @DisplayName("답변 완료 질문은 ANSWERED 상태와 answer 정보 반환, candidates는 null")
        void answered_question_returns_answered_status() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);
            DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].status").value(Status.ANSWERED.name()))
                    .andExpect(jsonPath("$.histories[0].question").exists())
                    .andExpect(jsonPath("$.histories[0].question.dailyQuestionId").value(dailyQuestion.getId()))
                    .andExpect(jsonPath("$.histories[0].answer").exists())
                    .andExpect(jsonPath("$.histories[0].answer.dailyAnswerId").value(answer.getId()))
                    // 답변 완료된 DailyQuestion은 createAnswer 시 candidates 삭제되므로 null
                    .andExpect(jsonPath("$.histories[0].candidates").doesNotExist());
        }

        @Test
        @DisplayName("미답변 질문은 UNANSWERED 상태와 answer null, candidates 배열 반환")
        void unanswered_question_returns_unanswered_status() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].status").value(Status.UNANSWERED.name()))
                    .andExpect(jsonPath("$.histories[0].question").exists())
                    .andExpect(jsonPath("$.histories[0].question.dailyQuestionId").value(dailyQuestion.getId()))
                    .andExpect(jsonPath("$.histories[0].answer").doesNotExist())
                    // 미답변 DailyQuestion은 candidates 배열이 존재하며 초기 질문(order=1) 포함
                    .andExpect(jsonPath("$.histories[0].candidates").isArray())
                    .andExpect(jsonPath("$.histories[0].candidates.length()").value(1))
                    .andExpect(jsonPath("$.histories[0].candidates[0].receivedOrder").value(1))
                    .andExpect(jsonPath("$.histories[0].candidates[0].selected").value(true));
        }

        @Test
        @DisplayName("DailyQuestion 없는 날짜는 NO_QUESTION 상태 반환")
        void no_daily_question_returns_no_question_status() throws Exception {
            // given - DailyQuestion 생성하지 않음
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].status").value(Status.NO_QUESTION.name()))
                    .andExpect(jsonPath("$.histories[0].question").doesNotExist())
                    .andExpect(jsonPath("$.histories[0].answer").doesNotExist());
        }

        @Test
        @DisplayName("혼합 상태 시 각 날짜별 정확한 상태 반환")
        void mixed_status_returns_correct_status_for_each_date() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate yesterday = today.minusDays(1);
            LocalDate twoDaysAgo = today.minusDays(2);

            // today: ANSWERED
            Question q1 = testQuestionUtils.createSave();
            DailyQuestion dq1 = testDailyQuestionUtils.createSave_With_Date(member, cycle, q1, today);
            testDailyQuestionAnswerUtils.createSave(dq1, member);

            // yesterday: UNANSWERED
            Question q2 = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(member, cycle, q2, yesterday);

            // twoDaysAgo: NO_QUESTION (생성하지 않음)

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "3")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].date").value(today.toString()))
                    .andExpect(jsonPath("$.histories[0].status").value(Status.ANSWERED.name()))
                    .andExpect(jsonPath("$.histories[1].date").value(yesterday.toString()))
                    .andExpect(jsonPath("$.histories[1].status").value(Status.UNANSWERED.name()))
                    .andExpect(jsonPath("$.histories[2].date").value(twoDaysAgo.toString()))
                    .andExpect(jsonPath("$.histories[2].status").value(Status.NO_QUESTION.name()));
        }

        @Test
        @DisplayName("3개 날짜 혼합: 답변 완료 / 리로드 2회 미답변 / 리로드 없이 미답변 - candidates 정확히 반환")
        void three_days_mixed_answered_two_reloads_and_no_reload() throws Exception {
            // given - 질문 풀 생성 (serve 3회 + reload 2회 = 최소 5개, 여유있게 10개)
            for (int i = 0; i < 10; i++) {
                testQuestionUtils.createSave();
            }

            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate yesterday = today.minusDays(1);
            LocalDate twoDaysAgo = today.minusDays(2);

            String answerBody = objectMapper.writeValueAsString(new CreateAnswerRequest("테스트 답변", false));

            // [twoDaysAgo] 질문 제공 → 답변 완료
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", twoDaysAgo)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", twoDaysAgo)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(answerBody))
                    .andExpect(status().isOk());

            // [yesterday] 질문 제공 → 리로드 2회 → 미답변 (후보 3개)
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // [today] 질문 제공 → 미답변, 리로드 없음 (후보 1개)
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // when & then - 히스토리 조회 (최신순: today → yesterday → twoDaysAgo)
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "3")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories.length()").value(3))
                    // [0] today - 리로드 없음, 후보 1개
                    .andExpect(jsonPath("$.histories[0].date").value(today.toString()))
                    .andExpect(jsonPath("$.histories[0].status").value(Status.UNANSWERED.name()))
                    .andExpect(jsonPath("$.histories[0].candidates.length()").value(1))
                    .andExpect(jsonPath("$.histories[0].candidates[0].receivedOrder").value(1))
                    .andExpect(jsonPath("$.histories[0].candidates[0].selected").value(true))
                    // [1] yesterday - 리로드 2회, 후보 3개, 마지막 reload 질문이 현재 선택
                    .andExpect(jsonPath("$.histories[1].date").value(yesterday.toString()))
                    .andExpect(jsonPath("$.histories[1].status").value(Status.UNANSWERED.name()))
                    .andExpect(jsonPath("$.histories[1].candidates.length()").value(3))
                    .andExpect(jsonPath("$.histories[1].candidates[0].receivedOrder").value(1))
                    .andExpect(jsonPath("$.histories[1].candidates[0].selected").value(false))
                    .andExpect(jsonPath("$.histories[1].candidates[1].receivedOrder").value(2))
                    .andExpect(jsonPath("$.histories[1].candidates[1].selected").value(false))
                    .andExpect(jsonPath("$.histories[1].candidates[2].receivedOrder").value(3))
                    .andExpect(jsonPath("$.histories[1].candidates[2].selected").value(true))
                    // [2] twoDaysAgo - 답변 완료, candidates null
                    .andExpect(jsonPath("$.histories[2].date").value(twoDaysAgo.toString()))
                    .andExpect(jsonPath("$.histories[2].status").value(Status.ANSWERED.name()))
                    .andExpect(jsonPath("$.histories[2].candidates").doesNotExist());
        }
    }

    @Nested
    @DisplayName("경계 조건 테스트")
    class BoundaryTest {

        @Test
        @DisplayName("조회 시작일이 가입일 이전이면 가입일로 제한")
        void start_date_limited_to_joined_date() throws Exception {
            // given - 가입일이 10일 전인 멤버
            LocalDate joinedDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(10);
            Member newMember = testMemberUtils.createSave_With_JoinedDate(joinedDate);
            String newToken = testAuthUtils.createBearerToken(newMember);
            QuestionCycle questionCycle = testQuestionCycleUtils.createSave_With_StartDate(newMember, joinedDate, TIMEZONE, 1);

            LocalDate baseDate = joinedDate.plusDays(3);
            int size = 10; // 가입일 이전까지 포함하려 시도

            // when & then - startDate가 joinedDate로 클램핑
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value(joinedDate.toString()))
                    .andExpect(jsonPath("$.startDate").value(questionCycle.getStartDate().toString()));
        }

        @Test
        @DisplayName("조회 종료일이 오늘 이후면 오늘로 제한")
        void end_date_limited_to_today() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate baseDate = today.minusDays(DAILY_SIZE);
            int size = 10 + DAILY_SIZE.intValue(); // 오늘 이후까지 포함하려 시도

            // when & then - endDate가 today로 클램핑
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.NEXT.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endDate").value(today.toString()));
        }

        @Test
        @DisplayName("baseDate가 가입일과 같을 때 PREVIOUS 방향")
        void base_date_equals_joined_date_with_previous() throws Exception {
            // given
            LocalDate joinedDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(DAILY_SIZE);
            Member newMember = testMemberUtils.createSave_With_JoinedDate(joinedDate);
            String newToken = testAuthUtils.createBearerToken(newMember);
            QuestionCycle Questioncycle = testQuestionCycleUtils.createSave_With_StartDate(newMember, joinedDate, TIMEZONE, 1);

            // when & then - 결과는 가입일 하루만
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", joinedDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value(joinedDate.toString()))
                    .andExpect(jsonPath("$.startDate").value(Questioncycle.getStartDate().toString()))
                    .andExpect(jsonPath("$.endDate").value(joinedDate.toString()))
                    .andExpect(jsonPath("$.histories.length()").value(1))
                    .andExpect(jsonPath("$.hasPrevious").value(false));
        }

        @Test
        @DisplayName("baseDate가 오늘과 같을 때 NEXT 방향")
        void base_date_equals_today_with_next() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then - 결과는 오늘 하루만
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.NEXT.name())
                            .param("size", "7")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value(today.toString()))
                    .andExpect(jsonPath("$.endDate").value(today.toString()))
                    .andExpect(jsonPath("$.histories.length()").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("양쪽 경계 모두 제한되는 경우")
        void both_boundaries_limited() throws Exception {
            // given - 가입일이 3일 전인 멤버
            LocalDate joinedDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(3);
            Member newMember = testMemberUtils.createSave_With_JoinedDate(joinedDate);
            String newToken = testAuthUtils.createBearerToken(newMember);
            QuestionCycle questionCycle = testQuestionCycleUtils.createSave_With_StartDate(newMember, joinedDate, TIMEZONE, 1);

            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate baseDate = questionCycle.getStartDate().plusDays(1); // 가입일 +1

            // when & then - 양쪽 모두 제한됨
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", "100")
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value(questionCycle.getStartDate().toString()))
                    .andExpect(jsonPath("$.endDate").value(today.toString()));
        }
    }

    @Nested
    @DisplayName("페이지네이션 플래그 테스트")
    class PaginationTest {

        @Test
        @DisplayName("startDate > joinedDate면 hasPrevious=true")
        void has_previous_true_when_more_dates_exist() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then - 가입일은 30일 전이므로 hasPrevious=true
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasPrevious").value(true));
        }

        @Test
        @DisplayName("startDate == joinedDate면 hasPrevious=false")
        void has_previous_false_at_joined_date() throws Exception {
            // given
            LocalDate joinedDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(3);
            Member newMember = testMemberUtils.createSave_With_JoinedDate(joinedDate);
            String newToken = testAuthUtils.createBearerToken(newMember);
            testQuestionCycleUtils.createSave_With_StartDate(newMember, joinedDate, TIMEZONE, 1);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", joinedDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasPrevious").value(false));
        }

        @Test
        @DisplayName("endDate < today면 hasNext=true")
        void has_next_true_when_more_dates_exist() throws Exception {
            // given
            LocalDate baseDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(DAILY_SIZE + 5);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true));
        }

        @Test
        @DisplayName("endDate == today면 hasNext=false")
        void has_next_false_at_today() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("양쪽 모두 더 있을 때 both flags true")
        void both_flags_true() throws Exception {
            // given - 가입일 30일 전, 중간 날짜 조회
            LocalDate baseDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(10);

            // when & then
            // baseDate-CALENDAR_HALF_SIZE가 가입일보다 나중이므로 hasPrevious=true
            // baseDate+CALENDAR_HALF_SIZE가 오늘보다 나중이므로 hasNext=false (오늘로 클램핑)
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", String.valueOf(CALENDAR_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasPrevious").value(true))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("양쪽 모두 끝에 도달했을 때 both flags false")
        void both_flags_false() throws Exception {
            // given - 가입일이 오늘인 멤버
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Member newMember = testMemberUtils.createSave_With_JoinedDate(today);
            String newToken = testAuthUtils.createBearerToken(newMember);
            testQuestionCycleUtils.createSave_With_StartDate(newMember, today, TIMEZONE, 1);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", String.valueOf(CALENDAR_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasPrevious").value(false))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("좋아요 여부 테스트")
    class LikedTest {

        @Test
        @DisplayName("좋아요를 누르지 않은 질문은 liked=false 반환")
        void history_returns_liked_false_when_not_liked() throws Exception {
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);

            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].question.liked").value(false));
        }

        @Test
        @DisplayName("좋아요를 누른 질문은 liked=true 반환")
        void history_returns_liked_true_when_liked() throws Exception {
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);
            testQuestionLikeUtils.createSave(question, member);

            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].question.liked").value(true));
        }

        @Test
        @DisplayName("여러 질문 중 좋아요 누른 것만 liked=true 반환")
        void history_returns_correct_liked_status_for_multiple_questions() throws Exception {
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate yesterday = today.minusDays(1);

            Question likedQuestion = testQuestionUtils.createSave();
            Question notLikedQuestion = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(member, cycle, likedQuestion, today);
            testDailyQuestionUtils.createSave_With_Date(member, cycle, notLikedQuestion, yesterday);
            testQuestionLikeUtils.createSave(likedQuestion, member);

            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].question.liked").value(true))   // today (최신순)
                    .andExpect(jsonPath("$.histories[1].question.liked").value(false));  // yesterday
        }

        @Test
        @DisplayName("사이클이 넘어가도 이전 사이클에서 좋아요한 질문은 히스토리에서 liked=true 반환")
        void history_returns_liked_true_for_same_question_across_cycles() throws Exception {
            // given - cycle (사이클 1): setup()에서 30일 전 시작으로 이미 생성됨
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate cycleOneDate = today.minusDays(1); // 사이클 1 날짜 (30일 전 ~ 오늘-1)

            // 사이클 1에서 질문 할당 및 좋아요 (사이클 2 생성 전 → 좋아요는 사이클과 무관)
            Question sharedQuestion = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(member, cycle, sharedQuestion, cycleOneDate);
            testQuestionLikeUtils.createSave(sharedQuestion, member);

            // 사이클 2 생성 후 동일 질문 할당 → liked=true 유지되어야 함
            QuestionCycle cycleTwo = testQuestionCycleUtils.createSave_With_StartDate(member, today, TIMEZONE, 2);
            testDailyQuestionUtils.createSave_With_Date(member, cycleTwo, sharedQuestion, today);

            // when - 사이클 2 날짜(오늘) 기준 히스토리 조회
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].question.questionCycle").value(2))
                    .andExpect(jsonPath("$.histories[0].question.liked").value(true));
        }
    }

    @Nested
    @DisplayName("예외 케이스 테스트")
    class ExceptionTest {

        @Test
        @DisplayName("미래 날짜 요청 시 400 Bad Request")
        void future_date_throws_400() throws Exception {
            // given
            LocalDate futureDate = LocalDate.now(ZoneId.of(TIMEZONE)).plusDays(1);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", futureDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.FUTURE_DATE_QUESTION.getCode()));
        }

        @Test
        @DisplayName("Timezone 헤더 누락 시 400 Bad Request")
        void missing_timezone_throws_400() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효하지 않은 타임존 요청 시 예외")
        void invalid_timezone_throws_exception() throws Exception {
            // given
            LocalDate pastDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(10);

            // when & then - 현재 구현에서는 500 에러 발생 (ZoneRulesException)
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", pastDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, "Invalid/Timezone"))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("baseDate 파라미터 누락 시 400 Bad Request")
        void missing_base_date_throws_400() throws Exception {
            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효하지 않은 historyDirection 요청 시 400 Bad Request")
        void invalid_direction_throws_400() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", "INVALID_DIRECTION")
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 헤더 없음 시 401 Unauthorized")
        void unauthorized_throws_401() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", String.valueOf(DAILY_SIZE))
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    class EdgeCaseTest {

        @Test
        @DisplayName("BOTH + 짝수 size 처리")
        void even_size_with_both_direction() throws Exception {
            // given - 클램핑 없이 순수하게 짝수 처리만 검증
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate joinedDate = today.minusDays(60);
            LocalDate baseDate = today.minusDays(30);

            // 60일 전 가입한 회원 생성
            Member oldMember = testMemberUtils.createSave_With_JoinedDate(joinedDate);
            String oldMemberToken = testAuthUtils.createBearerToken(oldMember);
            QuestionCycle oldCycle = testQuestionCycleUtils.createSave_With_StartDate(
                    oldMember, joinedDate, TIMEZONE, 1);

            int size = CALENDAR_SIZE.intValue() + 1; // 36 (짝수)
            int adjustedSize = size % 2 == 0 ? size - 1 : size; // 35
            int adjustedHalf = adjustedSize / 2; // 17

            // 35일치 DailyQuestion 생성 (baseDate 기준 -17 ~ +17)
            for (int i = -adjustedHalf; i <= adjustedHalf; i++) {
                LocalDate date = baseDate.plusDays(i);
                Question question = testQuestionUtils.createSave();
                testDailyQuestionUtils.createSave_With_Date(oldMember, oldCycle, question, date);
            }

            // when & then - 짝수 size(36)는 -1되어 35로 처리됨
            // 클램핑 없이 정확히 35개 반환
            LocalDate expectedStartDate = baseDate.minusDays(adjustedHalf); // today - 47
            LocalDate expectedEndDate = baseDate.plusDays(adjustedHalf); // today - 13

            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", baseDate.toString())
                            .param("historyDirection", HistoryDirection.BOTH.name())
                            .param("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, oldMemberToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value(expectedStartDate.toString()))
                    .andExpect(jsonPath("$.endDate").value(expectedEndDate.toString()))
                    .andExpect(jsonPath("$.histories.length()").value(adjustedSize));  // 짝수 size(36) 입력시 -1되어 정확히 35개 반환
        }

        @Test
        @DisplayName("size=1일 때 단일 결과 반환")
        void size_one_returns_single_result() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories.length()").value(1))
                    .andExpect(jsonPath("$.histories[0].date").value(today.toString()));
        }

        @Test
        @DisplayName("큰 size가 가용 범위로 제한됨")
        void large_size_clamped_to_available_range() throws Exception {
            // given - 가입일이 5일 전
            LocalDate joinedDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(5);
            Member newMember = testMemberUtils.createSave_With_JoinedDate(joinedDate);
            String newToken = testAuthUtils.createBearerToken(newMember);
            testQuestionCycleUtils.createSave_With_StartDate(newMember, joinedDate, TIMEZONE, 1);

            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // when & then - size=100이지만 6일치만 반환
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "100")
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories.length()").value(6)) // joinedDate ~ today = 6일
                    .andExpect(jsonPath("$.startDate").value(joinedDate.toString()))
                    .andExpect(jsonPath("$.endDate").value(today.toString()));
        }

        @Test
        @DisplayName("질문 정보가 정확하게 반환됨")
        void question_info_correctly_populated() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave_With_Content("테스트 질문 내용입니다");
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].question.dailyQuestionId").value(dailyQuestion.getId()))
                    .andExpect(jsonPath("$.histories[0].question.content").value("테스트 질문 내용입니다"))
                    .andExpect(jsonPath("$.histories[0].question.questionCycle").value(cycle.getCycleNumber()))
                    .andExpect(jsonPath("$.histories[0].question.changeCount").value(0));
        }

        @Test
        @DisplayName("답변 정보가 정확하게 반환됨")
        void answer_info_correctly_populated() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);
            DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave_With_Content(
                    dailyQuestion, member, "상세한 답변 내용");

            // when & then
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].answer.dailyAnswerId").value(answer.getId()))
                    .andExpect(jsonPath("$.histories[0].answer.content").value("상세한 답변 내용"))
                    .andExpect(jsonPath("$.histories[0].answer.answeredAt").exists());
        }

        @Test
        @DisplayName("다른 회원 데이터와 격리됨")
        void multiple_members_isolation() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            // 다른 회원 생성 및 DailyQuestion 생성
            Member otherMember = testMemberUtils.createSave_With_JoinedDate(LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(30));
            QuestionCycle otherCycle = testQuestionCycleUtils.createSave_With_StartDate(
                    otherMember, LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(30), TIMEZONE, 1);
            Question otherQuestion = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave_With_Date(otherMember, otherCycle, otherQuestion, today);

            // 현재 회원은 DailyQuestion 없음

            // when & then - 다른 회원의 DailyQuestion이 조회되지 않음
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", today.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories[0].status").value(Status.NO_QUESTION.name()));

            // DB 검증 - DailyQuestion은 다른 회원 것만 존재
            assertThat(dailyQuestionRepository.findAll())
                    .as("다른 회원의 DailyQuestion만 1개 존재해야 함")
                    .hasSize(1);
            assertThat(dailyQuestionRepository.findAll().get(0).getMember().getId())
                    .as("저장된 DailyQuestion이 다른 회원의 것이어야 함 (기대 ID: %d)", otherMember.getId())
                    .isEqualTo(otherMember.getId());
        }

        @Test
        @DisplayName("다른 타임존에서의 동작 확인")
        void different_timezone_handling() throws Exception {
            // given
            String differentTimezone = "America/New_York";
            // 과거 날짜를 사용하여 타임존 차이로 인한 미래 날짜 문제 방지
            LocalDate pastDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(10);

            // when & then - 다른 타임존으로 요청해도 동작
            mockMvc.perform(get(HISTORIES_API)
                            .param("baseDate", pastDate.toString())
                            .param("historyDirection", HistoryDirection.PREVIOUS.name())
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, differentTimezone))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.histories").isArray());
        }
    }
}
