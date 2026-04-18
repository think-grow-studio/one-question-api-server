package site.one_question.integrate.question;

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
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("질문 좋아요 토글 통합 테스트")
class ToggleLikeQuestionIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;
    private Question question;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        question = testQuestionUtils.createSave();
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("처음 좋아요 시 liked=true 반환")
        void toggle_like_first_time_returns_liked_true() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            assertThat(questionLikeRepository.findAll())
                    .as("좋아요가 1개 생성되어야 함")
                    .hasSize(1);
        }

        @Test
        @DisplayName("좋아요 취소 시 liked=false 반환")
        void toggle_like_second_time_returns_liked_false() throws Exception {
            testQuestionLikeUtils.createSave(question, member);

            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false));

            assertThat(questionLikeRepository.findAll())
                    .as("좋아요가 삭제되어 0개여야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("세 번 토글 시 liked=true 반환")
        void toggle_like_three_times_returns_liked_true() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.liked").value(true));

            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(jsonPath("$.liked").value(false));

            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            assertThat(questionLikeRepository.findAll())
                    .as("최종 좋아요 상태가 1개여야 함")
                    .hasSize(1);
        }

        @Test
        @DisplayName("여러 회원이 같은 질문에 좋아요 가능")
        void multiple_members_can_like_same_question() throws Exception {
            Member otherMember = testMemberUtils.createSave();
            String otherToken = testAuthUtils.createBearerToken(otherMember);

            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", question.getId())
                            .header(HttpHeaders.AUTHORIZATION, otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            assertThat(questionLikeRepository.findAll())
                    .as("좋아요가 2개 존재해야 함")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("존재하지 않는 questionId로 좋아요 시 404 응답")
        void toggle_like_nonexistent_question_returns_404() throws Exception {
            mockMvc.perform(post(QUESTIONS_API + "/{questionId}/like", 99999L)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.QUESTION_NOT_FOUND.getCode()));
        }
    }
}
