package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberPermission;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.test_config.IntegrateTest;

@DisplayName("후보 질문 선택 통합 테스트")
class SelectQuestionIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;
    private LocalDate today;
    private DailyQuestion dailyQuestion;
    // 초기 질문(order=1) 및 reload 후 추가된 후보(order=2)
    private Question initialQuestion;
    private Question reloadedQuestion;

    @BeforeEach
    void setup() throws Exception {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        today = LocalDate.now(ZoneId.of(TIMEZONE));

        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);

        // 질문 5개 이상 생성 (reload가 선택할 질문들)
        initialQuestion = testQuestionUtils.createSave();
        reloadedQuestion = testQuestionUtils.createSave();
        testQuestionUtils.createSave();
        testQuestionUtils.createSave();
        testQuestionUtils.createSave();

        // dailyQuestion 생성 (initialQuestion이 order=1 후보로 자동 저장됨)
        dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, initialQuestion);

        // reload API 호출 → reloadedQuestion이 order=2 후보로 추가되고 current question이 됨
        mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", today)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk());

        // reload 후 현재 상태 확인을 위해 캐시 클리어
        entityManager.clear();
        // reload 하여 새로 받은 question 으로 적용
        dailyQuestion = dailyQuestionRepository.findById(dailyQuestion.getId()).orElseThrow();
    }

    private String body(Long questionId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("questionId", questionId));
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("후보 선택 시 응답과 DB의 현재 질문이 함께 변경된다")
        void select_candidate_updates_current_question_in_response_and_db() throws Exception {
            // given - 현재 current가 reloadedQuestion, initialQuestion으로 변경
            Long targetId = initialQuestion.getId();
            assertThat(dailyQuestion.getQuestion().getId())
                    .as("reload 후 current는 initialQuestion이 아니어야 함")
                    .isNotEqualTo(targetId);

            // when
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(targetId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(targetId))
                    .andExpect(jsonPath("$.content").exists())
                    .andExpect(jsonPath("$.dailyQuestionId").value(dailyQuestion.getId()));

            // then
            entityManager.clear();
            DailyQuestion updated = dailyQuestionRepository.findById(dailyQuestion.getId()).orElseThrow();
            assertThat(updated.getQuestion().getId())
                    .as("후보 선택 후 dailyQuestion.question이 선택한 questionId로 변경되어야 함")
                    .isEqualTo(targetId);
        }

        @Test
        @DisplayName("후보 선택은 changeCount를 증가시키지 않는다")
        void select_question_does_not_increment_change_count() throws Exception {
            long beforeChangeCount = dailyQuestion.getChangeCount();

            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(initialQuestion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.changeCount").value(beforeChangeCount));

            entityManager.clear();
            DailyQuestion updated = dailyQuestionRepository.findById(dailyQuestion.getId()).orElseThrow();
            assertThat(updated.getChangeCount())
                    .as("후보 선택은 reload가 아니므로 changeCount가 유지되어야 함")
                    .isEqualTo((int) beforeChangeCount);
        }

        @Test
        @DisplayName("후보 선택 응답의 candidates 배열에 모든 후보 포함됨")
        void select_candidate_returns_all_candidates() throws Exception {
            // given - 후보 수: order=1(initialQuestion) + order=2(reloadedQuestion) = 2개
            int expectedCandidateCount = dailyQuestionCandidateRepository
                    .findAllByDailyQuestionOrderByReceivedOrderAsc(dailyQuestion).size();

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(initialQuestion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.candidates.length()").value(expectedCandidateCount))
                    .andExpect(jsonPath("$.candidates[0].questionId").value(initialQuestion.getId()))
                    .andExpect(jsonPath("$.candidates[0].selected").value(true))
                    .andExpect(jsonPath("$.candidates[1].questionId").value(dailyQuestion.getQuestion().getId()))
                    .andExpect(jsonPath("$.candidates[1].selected").value(false));
        }
    }

    @Nested
    @DisplayName("좋아요 여부 테스트")
    class LikedTest {

        @Test
        @DisplayName("좋아요 없는 후보 선택 시 liked=false 반환")
        void select_candidate_returns_liked_false() throws Exception {
            // given - 좋아요 없는 initialQuestion 선택
            Long targetId = initialQuestion.getId();

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(targetId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false));
        }

        @Test
        @DisplayName("좋아요 있는 후보 선택 시 liked=true 반환")
        void select_candidate_returns_liked_true() throws Exception {
            // given - initialQuestion에 좋아요 추가 후 선택
            testQuestionLikeUtils.createSave(initialQuestion, member);
            Long targetId = initialQuestion.getId();

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(targetId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));
        }
    }

    @Nested
    @DisplayName("멱등성 테스트")
    class IdempotencyTest {

        @Test
        @DisplayName("같은 후보를 두 번 선택해도 두 번째도 200 OK")
        void select_same_candidate_twice() throws Exception {
            Long targetId = initialQuestion.getId();

            // 첫 번째 선택
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(targetId)))
                    .andExpect(status().isOk());

            // 두 번째 선택 (같은 질문)
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(targetId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(targetId));
        }

        @Test
        @DisplayName("질문 변경 횟수가 최대여도 후보 재선택은 가능하다")
        void select_question_succeeds_even_when_change_count_is_max() throws Exception {
            int maxCount = MemberPermission.FREE.getMaxQuestionChangeCount();
            ReflectionTestUtils.setField(dailyQuestion, "changeCount", maxCount);
            dailyQuestionRepository.save(dailyQuestion);

            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(initialQuestion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(initialQuestion.getId()))
                    .andExpect(jsonPath("$.changeCount").value(maxCount));
        }
    }

    @Nested
    @DisplayName("예외 테스트")
    class ExceptionTest {

        @Test
        @DisplayName("후보에 없는 questionId 선택 시 404 CANDIDATE_NOT_FOUND")
        void select_nonexistent_candidate_returns_404() throws Exception {
            // given - 후보로 등록되지 않은 질문 ID (새로 생성)
            Question nonCandidate = testQuestionUtils.createSave();

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(nonCandidate.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.CANDIDATE_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("답변 완료 후 후보 선택 시 400 ALREADY_ANSWERED")
        void select_candidate_when_already_answered_returns_400() throws Exception {
            // given - 답변 생성
            testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(initialQuestion.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.ALREADY_ANSWERED.getCode()));
        }

        @Test
        @DisplayName("DailyQuestion 없는 날짜로 요청 시 404 DAILY_QUESTION_NOT_FOUND")
        void select_without_daily_question_returns_404() throws Exception {
            // given - DailyQuestion이 없는 날짜
            LocalDate futureDate = today.plusDays(30);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", futureDate)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(initialQuestion.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }
    }
}
