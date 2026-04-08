package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionCandidate;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.test_config.IntegrateTest;

@DisplayName("후보 질문 선택 통합 테스트")
class SelectCandidateIntegrateTest extends IntegrateTest {

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
        @DisplayName("후보 질문 선택 시 200 OK, 선택한 질문이 current로 변경됨")
        void select_candidate_returns_200_and_updates_current_question() throws Exception {
            // given - 오늘의 질문 + 리로드 질문 2개 준비
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question original = testQuestionUtils.createSave();
            Question reloaded = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, original);

            // 리로드 질문을 후보로 직접 추가
            DailyQuestionCandidate candidate2 = DailyQuestionCandidate.create(dailyQuestion, reloaded, 2);
            dailyQuestionCandidateRepository.save(candidate2);
            // changeCount=1로 설정 (리로드 1회 된 상태)
            ReflectionTestUtils.setField(dailyQuestion, "changeCount", 1);
            ReflectionTestUtils.setField(dailyQuestion, "question", reloaded);
            dailyQuestionRepository.save(dailyQuestion);

            // when - 원래 질문(original)을 선택
            String body = objectMapper.writeValueAsString(java.util.Map.of("questionId", original.getId()));

            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/candidate", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(original.getId()))
                    .andExpect(jsonPath("$.changeCount").value(1))  // changeCount 변화 없음
                    .andExpect(jsonPath("$.candidates.length()").value(2))
                    .andExpect(jsonPath("$.candidates[0].selected").value(true))   // order=1(original)이 selected
                    .andExpect(jsonPath("$.candidates[1].selected").value(false)); // order=2(reloaded)는 not selected
        }

        @Test
        @DisplayName("현재 선택된 질문을 재선택해도 200 OK")
        void select_already_selected_candidate_returns_200_ok() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question original = testQuestionUtils.createSave();
            testDailyQuestionUtils.createSave(member, cycle, original);  // current = original

            String body = objectMapper.writeValueAsString(java.util.Map.of("questionId", original.getId()));

            // when & then - 이미 선택된 질문을 다시 선택해도 ok
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/candidate", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questionId").value(original.getId()))
                    .andExpect(jsonPath("$.changeCount").value(0));
        }

        @Test
        @DisplayName("후보 선택 시 changeCount가 증가하지 않음")
        void select_candidate_does_not_increment_change_count() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question original = testQuestionUtils.createSave();
            Question candidate = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, original);

            DailyQuestionCandidate candidate2 = DailyQuestionCandidate.create(dailyQuestion, candidate, 2);
            dailyQuestionCandidateRepository.save(candidate2);
            ReflectionTestUtils.setField(dailyQuestion, "changeCount", 1);
            ReflectionTestUtils.setField(dailyQuestion, "question", candidate);
            dailyQuestionRepository.save(dailyQuestion);

            String body = objectMapper.writeValueAsString(java.util.Map.of("questionId", original.getId()));

            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/candidate", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            // DB 검증: changeCount 변화 없음
            entityManager.clear();
            DailyQuestion after = dailyQuestionRepository.findById(dailyQuestion.getId()).orElseThrow();
            assertThat(after.getChangeCount())
                    .as("후보 선택 후 changeCount가 변하지 않아야 함 (기대: 1)")
                    .isEqualTo(1);
            assertThat(after.getQuestion().getId())
                    .as("선택한 질문이 현재 질문으로 반영되어야 함")
                    .isEqualTo(original.getId());
        }
    }

    @Nested
    @DisplayName("예외 테스트")
    class ExceptionTest {

        @Test
        @DisplayName("후보 목록에 없는 질문 ID 선택 시 404 (QUESTION-010)")
        void select_non_candidate_question_throws_404() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question original = testQuestionUtils.createSave();
            Question other = testQuestionUtils.createSave();  // 후보 아님
            testDailyQuestionUtils.createSave(member, cycle, original);

            String body = objectMapper.writeValueAsString(java.util.Map.of("questionId", other.getId()));

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/candidate", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.CANDIDATE_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("답변한 질문에 대해 후보 선택 시 400 (QUESTION-005)")
        void select_candidate_after_answer_throws_400() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            Question original = testQuestionUtils.createSave();
            DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, original);
            testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);

            String body = objectMapper.writeValueAsString(java.util.Map.of("questionId", original.getId()));

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/candidate", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.ALREADY_ANSWERED.getCode()));
        }

        @Test
        @DisplayName("DailyQuestion 없이 후보 선택 시 404 (QUESTION-002)")
        void select_candidate_without_daily_question_throws_404() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
            Question question = testQuestionUtils.createSave();

            String body = objectMapper.writeValueAsString(java.util.Map.of("questionId", question.getId()));

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/candidate", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }
    }
}
