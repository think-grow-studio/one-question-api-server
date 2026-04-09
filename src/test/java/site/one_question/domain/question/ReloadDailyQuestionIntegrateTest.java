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
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberPermission;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
                    .andExpect(jsonPath("$.changeCount").value(1))
                    .andExpect(jsonPath("$.candidates.length()").value(2))
                    .andExpect(jsonPath("$.candidates[0].receivedOrder").value(1))
                    .andExpect(jsonPath("$.candidates[0].selected").value(false))
                    .andExpect(jsonPath("$.candidates[1].receivedOrder").value(2))
                    .andExpect(jsonPath("$.candidates[1].selected").value(true));

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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
    @DisplayName("좋아요 여부 테스트")
    class LikedTest {

        @Test
        @DisplayName("리로드 후 받은 질문에 좋아요를 누르지 않은 경우 liked=false 반환")
        void reload_returns_liked_false_when_not_liked() throws Exception {
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question question = testQuestionUtils.createSave();
            testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave(member, cycle, question);

            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false));
        }

        @Test
        @DisplayName("리로드 후 받은 질문에 좋아요를 누른 경우 liked=true 반환")
        void reload_returns_liked_true_when_new_question_is_liked() throws Exception {
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question original = testQuestionUtils.createSave();
            Question newQuestion = testQuestionUtils.createSave(); // 리로드 후 선택될 질문 (original 제외)
            testDailyQuestionUtils.createSave(member, cycle, original);
            testQuestionLikeUtils.createSave(newQuestion, member);

            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));
        }

        @Test
        @DisplayName("이전 사이클에서 좋아요한 질문으로 리로드 시 liked=true 반환")
        void reload_returns_liked_true_when_new_question_was_liked_in_previous_cycle() throws Exception {
            // given - 1년 전 사이클 1 시작, 오늘 사이클 2 시작
            LocalDate cycleOneStartDate = LocalDate.now(ZoneId.of(TIMEZONE)).minusYears(1);
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

            Member cycleMember = testMemberUtils.createSave_With_JoinedDate(cycleOneStartDate);
            String cycleMemberToken = testAuthUtils.createBearerToken(cycleMember);

            // 질문 풀: original(현재 할당) + sharedQuestion(리로드 후 선택될 유일한 후보)
            Question original = testQuestionUtils.createSave();
            Question sharedQuestion = testQuestionUtils.createSave();

            // 사이클 2 생성 전에 좋아요 → 좋아요는 사이클과 무관
            testQuestionLikeUtils.createSave(sharedQuestion, cycleMember);

            // 사이클 2 생성 후 original을 오늘의 질문으로 할당
            QuestionCycle cycleTwo = testQuestionCycleUtils.createSave_With_StartDate(cycleMember, today, TIMEZONE, 2);
            testDailyQuestionUtils.createSave(cycleMember, cycleTwo, original);

            // when - 리로드 시 original 제외 → sharedQuestion 선택
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, cycleMemberToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionCycle").value(2))
                    .andExpect(jsonPath("$.liked").value(true));
        }
    }

    @Nested
    @DisplayName("후보 제외 및 fallback 테스트")
    class CandidateExclusionTest {

        @Test
        @DisplayName("이전 날 DailyQuestion의 후보로 노출됐던 질문은 오늘 reload 시 나오지 않는다")
        void reload_excludes_previous_day_candidate_question() throws Exception {
            // given - 질문 4개: 어제 serve+reload(2개), 오늘 serve(1개), 미사용 fresh(1개)
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate yesterday = today.minusDays(1);

            Member newMember = testMemberUtils.createSave_With_JoinedDate(yesterday);
            String newToken = testAuthUtils.createBearerToken(newMember);

            long q1Id = testQuestionUtils.createSave().getId();
            long q2Id = testQuestionUtils.createSave().getId();
            long q3Id = testQuestionUtils.createSave().getId();
            long q4Id = testQuestionUtils.createSave().getId();

            // 어제: serve → reload (후보 2개 생성)
            String yServeRes = mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long yServedId = objectMapper.readTree(yServeRes).get("questionId").asLong();

            String yReloadRes = mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long yReloadId = objectMapper.readTree(yReloadRes).get("questionId").asLong();

            // 오늘: serve (serve는 served+candidatesInCycle 모두 제외하므로 미사용 질문이 배정됨)
            String tServeRes = mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long tServedId = objectMapper.readTree(tServeRes).get("questionId").asLong();

            // when - 오늘 reload (candidateQInCycle에 어제 후보 2개가 포함되어 모두 제외됨)
            String todayReloadRes = mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long todayReloadedId = objectMapper.readTree(todayReloadRes).get("questionId").asLong();

            // then - 4개 중 사용된 3개를 제외한 나머지 fresh 질문이 결정적으로 선택됨
            long freshId = java.util.stream.Stream.of(q1Id, q2Id, q3Id, q4Id)
                    .filter(id -> id != yServedId && id != yReloadId && id != tServedId)
                    .findFirst().orElseThrow();
            assertThat(todayReloadedId)
                    .as("어제 후보(serve+reload) 및 오늘 serve가 모두 제외되고 미사용 fresh 질문(ID: %d)이 선택되어야 함", freshId)
                    .isEqualTo(freshId);
        }

        @Test
        @DisplayName("served+candidate 모두 제외 시 served만 제외 fallback 동작 - 질문 풀 소진 상황")
        void reload_falls_back_to_served_only_when_pool_exhausted() throws Exception {
            // given - 질문 3개: 어제 serve→reload(2개), 오늘 serve(1개 - 유일하게 남은 질문)
            // allExcluded(servedInCycle + candidatesInCycle + todayExclude) = 전체 3개 → 빈 풀
            // fallback 1: servedInCycle만 제외 = [어제reload결과, 오늘serve결과] → 어제최초(yServedId) 선택
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate yesterday = today.minusDays(1);

            Member newMember = testMemberUtils.createSave_With_JoinedDate(yesterday);
            String newToken = testAuthUtils.createBearerToken(newMember);

            testQuestionUtils.createSave();
            testQuestionUtils.createSave();
            testQuestionUtils.createSave();

            // 어제: serve → reload
            String yServeRes = mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long yServedId = objectMapper.readTree(yServeRes).get("questionId").asLong();

            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // 오늘: serve (serve가 served+candidatesInCycle 모두 제외 → 3번째 질문이 결정적으로 배정됨)
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // when - 오늘 reload
            String todayReloadRes = mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long todayReloadedId = objectMapper.readTree(todayReloadRes).get("questionId").asLong();

            // then - fallback 1: servedInCycle 제외 후 어제 최초 제공된 질문(현재 served 아님,candidate 임)이 선택됨
            assertThat(todayReloadedId)
                    .as("fallback 1: 어제 최초 제공된 질문(yServedId: %d)이 선택되어야 함", yServedId)
                    .isEqualTo(yServedId);
        }

        @Test
        @DisplayName("served만 제외해도 비면 오늘 후보만 제외하는 fallback 2로 이전 제공 질문 재사용")
        void reload_falls_back_to_excluding_only_today_candidates() throws Exception {
            // given - 질문 2개: 어제 serve(1개), 오늘 serve(1개 - 유일하게 남은 질문)
            // allExcluded = 전체 → fallback 1: servedInCycle[어제,오늘] = 전체 → fallback 2: todayExclude[오늘] → 어제(yServedId)
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            LocalDate yesterday = today.minusDays(1);

            Member newMember = testMemberUtils.createSave_With_JoinedDate(yesterday);
            String newToken = testAuthUtils.createBearerToken(newMember);

            testQuestionUtils.createSave();
            testQuestionUtils.createSave();

            // 어제: serve
            String yServeRes = mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long yServedId = objectMapper.readTree(yServeRes).get("questionId").asLong();

            // 오늘: serve (유일하게 남은 질문이 결정적으로 배정됨)
            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // when - 오늘 reload (fallback 2 동작)
            String todayReloadRes = mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, newToken)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            long todayReloadedId = objectMapper.readTree(todayReloadRes).get("questionId").asLong();

            // then - fallback 2: 오늘 후보만 제외 → 어제 제공 질문이 재사용됨
            assertThat(todayReloadedId)
                    .as("fallback 2: 어제 제공 질문(yServedId: %d)이 재사용되어야 함", yServedId)
                    .isEqualTo(yServedId);
        }
    }

    @Nested
    @DisplayName("예외 테스트")
    class ExceptionTest {

        @Test
        @DisplayName("DailyQuestion 없이 새로고침 시 404")
        void reload_without_daily_question_throws_404() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
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

        @Test
        @DisplayName("선택 가능한 질문이 더 없으면 404 QUESTION_NOT_FOUND")
        void reload_when_no_question_available_throws_404() throws Exception {
            // given - 활성 질문이 1개뿐인 상태에서 serve API로 이미 할당됨
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            testQuestionUtils.createSave();

            mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk());

            // when & then - 풀에 더 이상 선택 가능한 질문 없음
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.QUESTION_NOT_FOUND.getCode()));
        }
    }
}
