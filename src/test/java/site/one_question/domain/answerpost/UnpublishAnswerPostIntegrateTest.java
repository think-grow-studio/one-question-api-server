package site.one_question.domain.answerpost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostStatus;
import site.one_question.api.answerpost.domain.exception.AnswerPostExceptionSpec;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.test_config.IntegrateTest;

@DisplayName("공개 답변 게시 취소 통합 테스트")
class UnpublishAnswerPostIntegrateTest extends IntegrateTest {

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
        @DisplayName("본인의 공개 답변 게시 취소 시 204 응답")
        void unpublish_own_answer_post_returns_204() throws Exception {
            // when & then
            mockMvc.perform(patch(ANSWER_POSTS_API + "/{id}/unpublish", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNoContent());

            // DB 검증
            AnswerPost updated = answerPostRepository.findById(answerPost.getId()).orElseThrow();
            assertThat(updated.getStatus())
                    .as("상태가 UNPUBLISHED로 변경되어야 함")
                    .isEqualTo(AnswerPostStatus.UNPUBLISHED);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("존재하지 않는 답변 게시 취소 시 404 응답")
        void unpublish_nonexistent_post_returns_404() throws Exception {
            // when & then
            mockMvc.perform(patch(ANSWER_POSTS_API + "/{id}/unpublish", 99999L)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(AnswerPostExceptionSpec.ANSWER_POST_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("다른 회원의 답변 게시 취소 시 403 응답")
        void unpublish_other_members_post_returns_403() throws Exception {
            // given
            Member otherMember = testMemberUtils.createSave();
            String otherToken = testAuthUtils.createBearerToken(otherMember);

            // when & then
            mockMvc.perform(patch(ANSWER_POSTS_API + "/{id}/unpublish", answerPost.getId())
                            .header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(AnswerPostExceptionSpec.ANSWER_POST_NOT_OWNED.getCode()));
        }
    }
}
