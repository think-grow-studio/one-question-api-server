package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberPermission;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.question.domain.exception.QuestionExceptionSpec;
import site.one_question.test_config.IntegrateTest;

@DisplayName("오늘의 질문 새로고침 통합 테스트")
class ReloadDailyQuestionIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("정상 새로고침 시 200 OK, 새 질문 반환")
        void reload_daily_question_success() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            testQuestionUtils.createSave(); // 새로고침용 추가 질문
            testDailyQuestionUtils.createSave(member, cycle, question);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyQuestionId").exists())
                    .andExpect(jsonPath("$.content").exists())
                    .andExpect(jsonPath("$.questionCycle").value(1));
        }

        @Test
        @DisplayName("새로고침 후 changeCount 1 증가")
        void reload_increments_change_count() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            testQuestionUtils.createSave(); // 새로고침용 추가 질문
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);

            assertThat(dailyQuestion.getChangeCount())
                    .as("초기 changeCount가 0이어야 함")
                    .isEqualTo(0);

            // when
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.changeCount").value(1));

            // then
            entityManager.clear();
            DailyQuestion reloaded = dailyQuestionRepository.findById(dailyQuestion.getId()).orElseThrow();
            assertThat(reloaded.getChangeCount())
                    .as("리로드 후 changeCount가 1 증가해야 함 (기대: 1)")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("새로고침 시 기존과 다른 질문 반환")
        void reload_returns_different_question() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question originalQuestion = testQuestionUtils.createSave();
            testQuestionUtils.createSave(); // 새로고침용 추가 질문
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, originalQuestion);

            Long originalQuestionId = originalQuestion.getId();

            // when
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // then
            entityManager.clear();
            DailyQuestion reloaded = dailyQuestionRepository.findById(dailyQuestion.getId()).orElseThrow();
            assertThat(reloaded.getQuestion().getId())
                    .as("리로드 후 질문 ID가 원래 질문 ID와 달라야 함 (원래 ID: %d)", originalQuestionId)
                    .isNotEqualTo(originalQuestionId);
        }

        @Test
        @DisplayName("질문 리로드시 과거 및 이전 질문과 중복되지 않는다")
        void reload_multiple_days_without_duplicates() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            LocalDate firstDay = today.minusDays(2);
            LocalDate secondDay = today.minusDays(1);
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);

            Question firstDayQuestion = testQuestionUtils.createSave();
            Question secondDayQuestion = testQuestionUtils.createSave();
            Question thirdDayQuestion = testQuestionUtils.createSave();
            testQuestionUtils.createSave(); // 첫 번째 새로고침 후보
            testQuestionUtils.createSave(); // 두 번째 새로고침 후보

            DailyQuestion firstDailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, firstDayQuestion, firstDay);
            testDailyQuestionAnswerUtils.createSave(firstDailyQuestion, member);

            DailyQuestion secondDailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, secondDayQuestion, secondDay);
            testDailyQuestionAnswerUtils.createSave(secondDailyQuestion, member);

            DailyQuestion todayDailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, thirdDayQuestion, today);

            Long firstDayQuestionId = firstDayQuestion.getId();
            Long secondDayQuestionId = secondDayQuestion.getId();
            Long initialThirdDayQuestionId = thirdDayQuestion.getId();

            // when - 첫 번째 새로고침 (3일차 1번 -> 2번 질문)
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            entityManager.clear();
            DailyQuestion firstReload = dailyQuestionRepository.findById(todayDailyQuestion.getId()).orElseThrow();
            Long firstReloadQuestionId = firstReload.getQuestion().getId();

            assertThat(firstReloadQuestionId)
                    .as("첫 번째 새로고침 후 질문이 첫째 날 질문과 달라야 함 (첫째 날 ID: %d)", firstDayQuestionId)
                    .isNotEqualTo(firstDayQuestionId)
                    .as("첫 번째 새로고침 후 질문이 둘째 날 질문과 달라야 함 (둘째 날 ID: %d)", secondDayQuestionId)
                    .isNotEqualTo(secondDayQuestionId)
                    .as("첫 번째 새로고침 후 질문이 셋째 날 초기 질문과 달라야 함 (초기 ID: %d)", initialThirdDayQuestionId)
                    .isNotEqualTo(initialThirdDayQuestionId);

            // when - 두 번째 새로고침 (3일차 2번 -> 3번 질문)
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            entityManager.clear();
            DailyQuestion secondReload = dailyQuestionRepository.findById(todayDailyQuestion.getId()).orElseThrow();
            Long secondReloadQuestionId = secondReload.getQuestion().getId();

            assertThat(secondReloadQuestionId)
                    .as("두 번째 새로고침 후 질문이 첫째 날 질문과 달라야 함 (첫째 날 ID: %d)", firstDayQuestionId)
                    .isNotEqualTo(firstDayQuestionId)
                    .as("두 번째 새로고침 후 질문이 둘째 날 질문과 달라야 함 (둘째 날 ID: %d)", secondDayQuestionId)
                    .isNotEqualTo(secondDayQuestionId)
                    .as("두 번째 새로고침 후 질문이 첫 번째 새로고침 질문과 달라야 함 (첫 번째 새로고침 ID: %d)", firstReloadQuestionId)
                    .isNotEqualTo(firstReloadQuestionId);
        }
    }

    @Nested
    @DisplayName("경계 테스트")
    class BoundaryTest {

        @Test
        @DisplayName("changeCount가 max-1일 때 마지막 새로고침 성공")
        void reload_at_max_minus_one_succeeds() throws Exception {
            // given - FREE 권한의 경우 maxChangeCount = 2, 따라서 1회 변경 후 한 번 더 가능
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            testQuestionUtils.createSave(); // 새로고침용 추가 질문
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);

            // changeCount를 max-1 (1)로 설정
            int maxCount = MemberPermission.FREE.getMaxQuestionChangeCount(); // 2
            ReflectionTestUtils.setField(dailyQuestion, "changeCount", maxCount - 1);
            dailyQuestionRepository.save(dailyQuestion);

            // when & then - 마지막 새로고침 성공
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.changeCount").value(maxCount));
        }

        @Test
        @DisplayName("changeCount가 max일 때 새로고침 실패 (400)")
        void reload_at_max_count_fails() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);

            // changeCount를 max (2)로 설정
            int maxCount = MemberPermission.FREE.getMaxQuestionChangeCount(); // 2
            ReflectionTestUtils.setField(dailyQuestion, "changeCount", maxCount);
            dailyQuestionRepository.save(dailyQuestion);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.RELOAD_LIMIT_EXCEEDED.getCode()));
        }
    }

    @Nested
    @DisplayName("예외 테스트")
    class ExceptionTest {

        @Test
        @DisplayName("DailyQuestion 없이 새로고침 시 404")
        void reload_without_daily_question_throws_404() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            // DailyQuestion 생성하지 않음

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("답변한 질문 새로고침 시 400 (QUESTION-005)")
        void reload_answered_question_throws_400() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);

            // 답변 생성
            testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.ALREADY_ANSWERED.getCode()));
        }

        @Test
        @DisplayName("변경 횟수 초과 시 400 (QUESTION-008)")
        void reload_exceeded_limit_throws_400() throws Exception {
            // given
            LocalDate today = LocalDate.now();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);

            // changeCount를 max 초과로 설정
            int exceedCount = MemberPermission.FREE.getMaxQuestionChangeCount() + 1;
            ReflectionTestUtils.setField(dailyQuestion, "changeCount", exceedCount);
            dailyQuestionRepository.save(dailyQuestion);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.RELOAD_LIMIT_EXCEEDED.getCode()));
        }
    }
}
