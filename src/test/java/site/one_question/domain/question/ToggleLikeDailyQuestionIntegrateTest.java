package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.test_config.IntegrateTest;

@DisplayName("오늘의 질문 좋아요 토글 통합 테스트")
class ToggleLikeDailyQuestionIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;
    private DailyQuestion dailyQuestion;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("처음 좋아요 시 liked=true 반환")
        void toggle_like_first_time_returns_liked_true() throws Exception {
            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", dailyQuestion.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            // DB 검증
            assertThat(dailyQuestionLikeRepository.findAll())
                    .as("좋아요가 1개 생성되어야 함")
                    .hasSize(1);
        }

        @Test
        @DisplayName("좋아요 취소 시 liked=false 반환")
        void toggle_like_second_time_returns_liked_false() throws Exception {
            // given - 좋아요 1회
            testDailyQuestionLikeUtils.createSave(dailyQuestion, member);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", dailyQuestion.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false));

            // DB 검증
            assertThat(dailyQuestionLikeRepository.findAll())
                    .as("좋아요가 삭제되어 0개여야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("세 번 토글 시 liked=true 반환")
        void toggle_like_three_times_returns_liked_true() throws Exception {
            // 1회: 좋아요
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", dailyQuestion.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.liked").value(true));

            // 2회: 좋아요 취소
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", dailyQuestion.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.liked").value(false));

            // 3회: 다시 좋아요
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", dailyQuestion.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            // DB 검증
            assertThat(dailyQuestionLikeRepository.findAll())
                    .as("최종 좋아요 상태가 1개여야 함")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("존재하지 않는 dailyQuestionId로 좋아요 시 404 응답")
        void toggle_like_nonexistent_daily_question_returns_404() throws Exception {
            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", 99999L)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("다른 회원의 질문에 좋아요 시 404 응답")
        void toggle_like_other_member_daily_question_returns_404() throws Exception {
            // given - 다른 회원의 질문
            Member otherMember = testMemberUtils.createSave();
            QuestionCycle otherCycle = testQuestionCycleUtils.createSave(otherMember);
            Question otherQuestion = testQuestionUtils.createSave();
            DailyQuestion otherDailyQuestion = testDailyQuestionUtils.createSave(otherMember, otherCycle, otherQuestion);

            // when & then - 내 토큰으로 다른 사람의 질문에 좋아요
            mockMvc.perform(post(QUESTIONS_API + "/{dailyQuestionId}/like", otherDailyQuestion.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }
    }
}
