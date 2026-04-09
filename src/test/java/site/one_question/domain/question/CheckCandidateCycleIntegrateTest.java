package site.one_question.domain.question;

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
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionCandidate;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.test_config.IntegrateTest;

@DisplayName("후보 질문 과거 배정 여부 확인 통합 테스트")
class CheckCandidateCycleIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;
    private LocalDate today;
    private LocalDate twoDaysAgo;
    private LocalDate yesterday;
    private Question repeatedQuestion;
    private Question todayQuestion;
    private Question freshCandidateQuestion;
    private Question nonCandidateQuestion;

    @BeforeEach
    void setup() {
        today = LocalDate.now(ZoneId.of(TIMEZONE));
        yesterday = today.minusDays(1);
        twoDaysAgo = today.minusDays(2);

        member = testMemberUtils.createSave_With_JoinedDate(twoDaysAgo);
        token = testAuthUtils.createBearerToken(member);

        // 모든 날짜를 같은 cycle 안에 묶어 "같은 cycle 내 과거 배정 여부"를 검증한다.
        QuestionCycle cycle = testQuestionCycleUtils.createSave_With_StartDate(member, twoDaysAgo, TIMEZONE, 1);

        // repeatedQuestion: 과거(2일 전, 어제)에 이미 배정된 질문. 오늘 후보에도 포함시켜 duplicate 케이스를 만든다.
        // todayQuestion: 오늘 current question. 오늘 배정만으로는 duplicate로 보지 않는 케이스를 검증한다.
        // freshCandidateQuestion: 오늘 후보이지만 과거 배정 이력이 없는 질문이다.
        // nonCandidateQuestion: 오늘 후보에 포함되지 않은 질문으로 404 케이스를 검증한다.
        repeatedQuestion = testQuestionUtils.createSave();
        todayQuestion = testQuestionUtils.createSave();
        freshCandidateQuestion = testQuestionUtils.createSave();
        nonCandidateQuestion = testQuestionUtils.createSave();

        // 동일한 질문을 이전 날짜에 넣어, check가 제대로 동작하는지 확인한다.
        testDailyQuestionUtils.createSave_With_Date(member, cycle, repeatedQuestion, twoDaysAgo);
        testDailyQuestionUtils.createSave_With_Date(member, cycle, repeatedQuestion, yesterday);

        // 오늘 DailyQuestion에는 현재 선택 질문(order=1)과 추가 후보(order=2,3)를 함께 둔다.
        // cycle-check는 "오늘 후보에 포함된 질문인지"를 먼저 검증하므로 후보 구성이 중요하다.
        DailyQuestion todayDailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, todayQuestion, today);
        dailyQuestionCandidateRepository.save(DailyQuestionCandidate.create(todayDailyQuestion, repeatedQuestion, 2));
        dailyQuestionCandidateRepository.save(DailyQuestionCandidate.create(todayDailyQuestion, freshCandidateQuestion, 3));
    }

    private String body(Long questionId) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("questionId", questionId));
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("같은 사이클 내 여러 이전 날짜에 배정된 후보면 모든 날짜를 반환")
        void check_candidate_returns_true_and_assigned_dates() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/candidates/cycle-check", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(repeatedQuestion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alreadyAssignedInCycle").value(true))
                    .andExpect(jsonPath("$.previouslyAssignedDates.length()").value(2))
                    .andExpect(jsonPath("$.previouslyAssignedDates[0]").value(twoDaysAgo.toString()))
                    .andExpect(jsonPath("$.previouslyAssignedDates[1]").value(yesterday.toString()));
        }

        @Test
        @DisplayName("같은 사이클 내 이전 배정 이력이 없는 후보면 alreadyAssignedInCycle=false와 빈 배열을 반환")
        void check_candidate_returns_false_when_not_assigned_before() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/candidates/cycle-check", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(freshCandidateQuestion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alreadyAssignedInCycle").value(false))
                    .andExpect(jsonPath("$.previouslyAssignedDates").isArray())
                    .andExpect(jsonPath("$.previouslyAssignedDates.length()").value(0));
        }

        @Test
        @DisplayName("오늘 현재 선택된 질문은 오늘 배정만으로는 cycle 중복으로 간주되지 않는다")
        void check_candidate_excludes_today_current_question_from_previous_assignments() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/candidates/cycle-check", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(todayQuestion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alreadyAssignedInCycle").value(false))
                    .andExpect(jsonPath("$.previouslyAssignedDates").isArray())
                    .andExpect(jsonPath("$.previouslyAssignedDates.length()").value(0));
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("오늘 후보에 없는 질문이면 404 CANDIDATE_NOT_FOUND")
        void check_candidate_returns_404_when_question_is_not_today_candidate() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/candidates/cycle-check", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(nonCandidateQuestion.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.CANDIDATE_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("해당 날짜의 DailyQuestion이 없으면 404 DAILY_QUESTION_NOT_FOUND")
        void check_candidate_returns_404_when_daily_question_not_found() throws Exception {
            LocalDate twoDaysLater = today.plusDays(2);

            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/candidates/cycle-check", twoDaysLater)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(repeatedQuestion.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }
    }
}
