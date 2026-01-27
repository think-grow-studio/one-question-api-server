package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.question.domain.exception.QuestionExceptionSpec;
import site.one_question.test_config.IntegrateTest;

@DisplayName("오늘의 질문 조회 통합 테스트")
class ServeDailyQuestionIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Test
    @DisplayName("오늘의 질문 조회 시 200 OK 응답 및 질문 정보 반환")
    void serve_daily_question_with_valid_request_then_return_200_ok() throws Exception {
        // given
        LocalDate today = LocalDate.now();

        // when & then
        mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyQuestionId").exists())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.questionCycle").value(1))
                .andExpect(jsonPath("$.changeCount").value(0));

        // DB 검증 - DailyQuestion 생성 확인
        assertThat(dailyQuestionRepository.findAll()).hasSize(1);

        DailyQuestion savedDailyQuestion = dailyQuestionRepository.findAll().get(0);
        assertThat(savedDailyQuestion.getMember().getId()).isEqualTo(member.getId());
        assertThat(savedDailyQuestion.getDate()).isEqualTo(today);

        // QuestionCycle 생성 확인
        assertThat(questionCycleRepository.findAll()).hasSize(1);
    }

    @Nested
    @DisplayName("멱등성 테스트")
    class IdempotencyTest {

        @Test
        @DisplayName("동일 날짜 2번 요청 시 같은 DailyQuestion 반환")
        void serve_same_date_twice_returns_same_daily_question() throws Exception {
            // given
            LocalDate today = LocalDate.now();

            // when - 첫 번째 요청
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            List<DailyQuestion> afterFirstRequest = dailyQuestionRepository.findAll();
            assertThat(afterFirstRequest).hasSize(1);
            Long firstDailyQuestionId = afterFirstRequest.get(0).getId();

            // when - 두 번째 요청 (동일 날짜)
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyQuestionId").value(firstDailyQuestionId));

            // then - DailyQuestion이 1개만 존재 (중복 생성 없음)
            assertThat(dailyQuestionRepository.findAll()).hasSize(1);
            assertThat(questionCycleRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("갭 필링 테스트")
    class GapFillingTest {

        @Test
        @DisplayName("2년 후 요청 시 중간 사이클들 자동 생성")
        void request_after_2_years_creates_intermediate_cycles() throws Exception {
            // given - 2년 전에 가입한 멤버 및 첫 번째 사이클 생성
            LocalDate twoYearsAgo = LocalDate.now().minusYears(2);
            Member oldMember = testMemberUtils.createSave_With_JoinedDate(twoYearsAgo);
            String oldMemberToken = testAuthUtils.createBearerToken(oldMember);

            // 첫 번째 사이클 생성 (2년 전 시작)
            testQuestionCycleUtils.createSave_With_StartDate(oldMember, twoYearsAgo, TIMEZONE, 1);

            LocalDate today = LocalDate.now();

            // when - 오늘 날짜로 질문 요청
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, oldMemberToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionCycle").value(3)); // 3번째 사이클

            // then - 사이클이 순차적으로 생성됨
            List<QuestionCycle> cycles = questionCycleRepository.findAll();
            assertThat(cycles).hasSize(3);

            // cycleNumber 순서 검증
            List<QuestionCycle> sortedCycles = cycles.stream()
                    .sorted(Comparator.comparing(QuestionCycle::getCycleNumber))
                    .toList();

            assertThat(sortedCycles.get(0).getCycleNumber()).isEqualTo(1);
            assertThat(sortedCycles.get(1).getCycleNumber()).isEqualTo(2);
            assertThat(sortedCycles.get(2).getCycleNumber()).isEqualTo(3);

            // 각 사이클의 시작/종료일 연속성 검증
            for (int i = 1; i < sortedCycles.size(); i++) {
                QuestionCycle prevCycle = sortedCycles.get(i - 1);
                QuestionCycle currentCycle = sortedCycles.get(i);
                assertThat(currentCycle.getStartDate())
                        .isEqualTo(prevCycle.getEndDate().plusDays(1));
            }
        }
    }

    @Nested
    @DisplayName("사이클 경계 테스트")
    class CycleBoundaryTest {

        @Test
        @DisplayName("사이클 마지막 날 요청 시 해당 사이클 반환")
        void request_on_cycle_end_date_returns_current_cycle() throws Exception {
            // given - 1년 전에 가입해서 오늘이 사이클 마지막 날이 되도록 설정
            LocalDate cycleStartDate = LocalDate.now().minusYears(1).plusDays(1);
            LocalDate cycleEndDate = cycleStartDate.plusYears(1).minusDays(1); // = 오늘

            Member memberWithCycle = testMemberUtils.createSave_With_JoinedDate(cycleStartDate);
            String memberToken = testAuthUtils.createBearerToken(memberWithCycle);

            QuestionCycle cycle = testQuestionCycleUtils.createSave_With_StartDate(
                    memberWithCycle, cycleStartDate, TIMEZONE, 1);

            // when - 사이클 종료일에 요청
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", cycleEndDate)
                            .header(HttpHeaders.AUTHORIZATION, memberToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionCycle").value(1)); // 첫 번째 사이클

            // then - 새 사이클이 생성되지 않음
            assertThat(questionCycleRepository.findAll()).hasSize(1);
            assertThat(questionCycleRepository.findAll().get(0).getId()).isEqualTo(cycle.getId());
        }

        @Test
        @DisplayName("사이클 종료일 다음 날 요청 시 새 사이클 생성")
        void request_day_after_cycle_end_creates_new_cycle() throws Exception {
            // given - 사이클 종료일이 어제가 되도록 설정
            LocalDate cycleStartDate = LocalDate.now().minusYears(1);
            LocalDate cycleEndDate = cycleStartDate.plusYears(1).minusDays(1); // = 어제
            LocalDate dayAfterCycleEnd = cycleEndDate.plusDays(1); // = 오늘

            Member memberWithCycle = testMemberUtils.createSave_With_JoinedDate(cycleStartDate);
            String memberToken = testAuthUtils.createBearerToken(memberWithCycle);

            testQuestionCycleUtils.createSave_With_StartDate(memberWithCycle, cycleStartDate, TIMEZONE, 1);

            // when - 사이클 종료일 다음 날에 요청
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", dayAfterCycleEnd)
                            .header(HttpHeaders.AUTHORIZATION, memberToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionCycle").value(2)); // 두 번째 사이클

            // then - 새 사이클 생성 확인
            List<QuestionCycle> cycles = questionCycleRepository.findAll();
            assertThat(cycles).hasSize(2);

            QuestionCycle newCycle = cycles.stream()
                    .filter(c -> c.getCycleNumber() == 2)
                    .findFirst()
                    .orElseThrow();

            assertThat(newCycle.getStartDate()).isEqualTo(dayAfterCycleEnd);
        }
    }

    @Nested
    @DisplayName("예외 처리 테스트")
    class ExceptionTest {

        @Test
        @DisplayName("미래 날짜 요청 시 400 Bad Request")
        void request_future_date_throws_400_bad_request() throws Exception {
            // given
            LocalDate futureDate = LocalDate.now().plusDays(1);

            // when & then
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", futureDate)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.FUTURE_DATE_QUESTION.getCode()));

            // DB 검증 - 아무것도 생성되지 않음
            assertThat(dailyQuestionRepository.findAll()).isEmpty();
            assertThat(questionCycleRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("가입일 이전 날짜 요청 시 400 Bad Request")
        void request_before_signup_date_throws_400_bad_request() throws Exception {
            // given - 오늘 가입한 멤버에 대해 첫 사이클 생성
            LocalDate today = LocalDate.now();
            testQuestionCycleUtils.createSave_With_StartDate(member, today, TIMEZONE, 1);

            LocalDate beforeSignupDate = today.minusDays(1);

            // when & then
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", beforeSignupDate)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.BEFORE_SIGNUP_DATE.getCode()));

            // DB 검증 - 새 사이클이 생성되지 않음
            assertThat(questionCycleRepository.findAll()).hasSize(1);
            assertThat(dailyQuestionRepository.findAll()).isEmpty();
        }
    }
}
