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
import org.springframework.http.MediaType;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostStatus;
import site.one_question.api.answerpost.domain.exception.AnswerPostExceptionSpec;
import site.one_question.api.answerpost.presentation.request.PublishAnswerPostRequest;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.test_config.IntegrateTest;

@DisplayName("답변 공개 게시 통합 테스트")
class PublishAnswerPostIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;
    private QuestionCycle cycle;
    private DailyQuestionAnswer questionAnswer;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
        questionAnswer = testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("새로운 답변 공개 게시 시 201 응답")
        void publish_new_answer_post_returns_201_created() throws Exception {
            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PublishAnswerPostRequest(questionAnswer.getId()))))
                    .andExpect(status().isCreated());

            // DB 검증
            assertThat(answerPostRepository.findAll())
                    .as("AnswerPost가 1개 생성되어야 함")
                    .hasSize(1);

            AnswerPost saved = answerPostRepository.findAll().get(0);
            assertThat(saved.getStatus())
                    .as("게시 상태가 PUBLISHED여야 함")
                    .isEqualTo(AnswerPostStatus.PUBLISHED);
            assertThat(saved.getMember().getId())
                    .as("작성자 ID가 일치해야 함")
                    .isEqualTo(member.getId());
            assertThat(saved.getAnonymousNickname())
                    .as("익명 닉네임이 비어있지 않아야 함")
                    .isNotBlank();
        }

        @Test
        @DisplayName("게시 취소된 답변 재게시 시 201 응답")
        void republish_unpublished_answer_post_returns_201() throws Exception {
            // given - 게시 후 게시 취소
            AnswerPost answerPost = testAnswerPostUtils.createSave_Unpublished(questionAnswer, member);

            assertThat(answerPost.getStatus())
                    .as("게시 취소 상태 확인")
                    .isEqualTo(AnswerPostStatus.UNPUBLISHED);

            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PublishAnswerPostRequest(questionAnswer.getId()))))
                    .andExpect(status().isCreated());

            // DB 검증 - 새로 생성되지 않고 기존 게시물이 재게시됨
            assertThat(answerPostRepository.findAll())
                    .as("AnswerPost가 1개만 존재해야 함 (새로 생성되지 않음)")
                    .hasSize(1);

            AnswerPost republished = answerPostRepository.findAll().get(0);
            assertThat(republished.getId())
                    .as("기존 게시물 ID가 유지되어야 함")
                    .isEqualTo(answerPost.getId());
            assertThat(republished.getStatus())
                    .as("상태가 PUBLISHED로 변경되어야 함")
                    .isEqualTo(AnswerPostStatus.PUBLISHED);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("이미 공개된 답변 재게시 시 409 응답")
        void publish_already_published_returns_409_conflict() throws Exception {
            // given - 이미 게시된 상태
            testAnswerPostUtils.createSave(questionAnswer, member);

            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PublishAnswerPostRequest(questionAnswer.getId()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(AnswerPostExceptionSpec.ALREADY_PUBLISHED.getCode()));
        }

        @Test
        @DisplayName("존재하지 않는 답변 ID로 게시 시 404 응답")
        void publish_with_nonexistent_answer_id_returns_404() throws Exception {
            // when & then
            mockMvc.perform(post(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PublishAnswerPostRequest(99999L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.ANSWER_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("다른 회원의 답변 게시 시 403 응답")
        void publish_other_members_answer_returns_403() throws Exception {
            // given - 다른 회원
            Member otherMember = testMemberUtils.createSave();
            String otherToken = testAuthUtils.createBearerToken(otherMember);

            // when & then - 다른 회원이 내 답변을 게시 시도
            mockMvc.perform(post(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PublishAnswerPostRequest(questionAnswer.getId()))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(AnswerPostExceptionSpec.ANSWER_POST_NOT_OWNED.getCode()));
        }
    }
}
