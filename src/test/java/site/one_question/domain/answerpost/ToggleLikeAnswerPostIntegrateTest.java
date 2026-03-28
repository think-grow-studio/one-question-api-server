package site.one_question.domain.answerpost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.exception.AnswerPostExceptionSpec;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.test_config.IntegrateTest;

@DisplayName("공개 답변 좋아요 토글 통합 테스트")
class ToggleLikeAnswerPostIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;
    private QuestionCycle cycle;
    private AnswerPost answerPost;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
        DailyQuestionAnswer questionAnswer = testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);
        answerPost = testAnswerPostUtils.createSave(questionAnswer, member);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("처음 좋아요 시 liked=true 반환")
        void toggle_like_first_time_returns_liked_true() throws Exception {
            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            // DB 검증
            assertThat(answerPostLikeRepository.findAll())
                    .as("좋아요가 1개 생성되어야 함")
                    .hasSize(1);
        }

        @Test
        @DisplayName("좋아요 취소 시 liked=false 반환")
        void toggle_like_second_time_returns_liked_false() throws Exception {
            // given - 좋아요 1회
            testAnswerPostLikeUtils.createSave(answerPost, member);

            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false));

            // DB 검증
            assertThat(answerPostLikeRepository.findAll())
                    .as("좋아요가 삭제되어 0개여야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("세 번 토글 시 liked=true 반환")
        void toggle_like_three_times_returns_liked_true() throws Exception {
            // 1회: 좋아요
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.liked").value(true));

            // 2회: 좋아요 취소
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.liked").value(false));

            // 3회: 다시 좋아요
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            // DB 검증
            assertThat(answerPostLikeRepository.findAll())
                    .as("최종 좋아요 상태가 1개여야 함")
                    .hasSize(1);
        }

        @Test
        @DisplayName("여러 회원이 같은 게시물에 좋아요 가능")
        void multiple_members_can_like_same_post() throws Exception {
            // given
            Member otherMember = testMemberUtils.createSave();
            String otherToken = testAuthUtils.createBearerToken(otherMember);

            // when - 두 회원이 각각 좋아요
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            // then
            assertThat(answerPostLikeRepository.findAll())
                    .as("좋아요가 2개 존재해야 함")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("존재하지 않는 게시물 좋아요 시 404 응답")
        void toggle_like_nonexistent_post_returns_404() throws Exception {
            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API + "/{id}/like", 99999L)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(AnswerPostExceptionSpec.ANSWER_POST_NOT_FOUND.getCode()));
        }
    }
}
