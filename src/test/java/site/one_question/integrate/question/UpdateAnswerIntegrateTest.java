package site.one_question.integrate.question;

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
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostStatus;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.api.question.presentation.request.UpdateAnswerRequest;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("답변 수정 통합 테스트")
class UpdateAnswerIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;
    private DailyQuestion dailyQuestion;
    private DailyQuestionAnswer answer;
    private LocalDate today;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        today = LocalDate.now(ZoneId.of(TIMEZONE));
        dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
        answer = testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("답변 수정 + publish 미전송 시 내용만 수정되고 게시 상태 변경 없음")
        void update_answer_without_publish_only_updates_content() throws Exception {
            // given
            String requestBody = """
                    { "answer": "수정된 답변" }
                    """;

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("수정된 답변"))
                    .andExpect(jsonPath("$.published").value(false));

            assertThat(answerPostRepository.findAll())
                    .as("publish 미전송이므로 AnswerPost가 생성되지 않아야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("답변 수정 + publish=true 시 AnswerPost 생성")
        void update_answer_with_publish_true_creates_answer_post() throws Exception {
            // given
            UpdateAnswerRequest request = new UpdateAnswerRequest("공개 수정", true);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("공개 수정"))
                    .andExpect(jsonPath("$.published").value(true));

            assertThat(answerPostRepository.findAll())
                    .as("publish=true이므로 AnswerPost가 1개 생성되어야 함")
                    .hasSize(1);

            AnswerPost savedPost = answerPostRepository.findAll().get(0);
            assertThat(savedPost.getStatus())
                    .as("게시 상태가 PUBLISHED여야 함")
                    .isEqualTo(AnswerPostStatus.PUBLISHED);
        }

        @Test
        @DisplayName("답변 수정 + publish=false 시 기존 AnswerPost 게시 취소")
        void update_answer_with_publish_false_unpublishes_answer_post() throws Exception {
            // given - 이미 게시된 상태
            testAnswerPostUtils.createSave(answer, member);
            UpdateAnswerRequest request = new UpdateAnswerRequest("비공개 수정", false);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("비공개 수정"))
                    .andExpect(jsonPath("$.published").value(false));

            AnswerPost updatedPost = answerPostRepository.findAll().get(0);
            assertThat(updatedPost.getStatus())
                    .as("게시 상태가 UNPUBLISHED여야 함")
                    .isEqualTo(AnswerPostStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("답변 수정 + publish=true로 UNPUBLISHED 답변 재게시")
        void update_answer_with_publish_true_republishes_unpublished_post() throws Exception {
            // given - 게시 취소된 상태
            AnswerPost unpublishedPost = testAnswerPostUtils.createSave_Unpublished(answer, member);
            assertThat(unpublishedPost.getStatus()).isEqualTo(AnswerPostStatus.UNPUBLISHED);

            UpdateAnswerRequest request = new UpdateAnswerRequest("재게시 수정", true);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(true));

            assertThat(answerPostRepository.findAll())
                    .as("새로 생성되지 않고 기존 AnswerPost 1개만 존재해야 함")
                    .hasSize(1);

            AnswerPost republished = answerPostRepository.findAll().get(0);
            assertThat(republished.getId())
                    .as("기존 게시물 ID가 유지되어야 함")
                    .isEqualTo(unpublishedPost.getId());
            assertThat(republished.getStatus())
                    .as("상태가 PUBLISHED로 변경되어야 함")
                    .isEqualTo(AnswerPostStatus.PUBLISHED);
        }

        @Test
        @DisplayName("이미 published인 답변에 publish=true 시 멱등하게 동작")
        void update_answer_with_publish_true_on_already_published_is_idempotent() throws Exception {
            // given - 이미 게시된 상태
            testAnswerPostUtils.createSave(answer, member);
            UpdateAnswerRequest request = new UpdateAnswerRequest("멱등 수정", true);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(true));

            assertThat(answerPostRepository.findAll())
                    .as("AnswerPost가 1개만 존재해야 함 (중복 생성 없음)")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("답변이 없는 날짜로 수정 요청 시 404 응답")
        void update_answer_without_existing_answer_returns_404() throws Exception {
            // given - 답변이 없는 새 DailyQuestion
            Question newQuestion = testQuestionUtils.createSave();
            QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
            LocalDate yesterday = today.minusDays(1);
            testDailyQuestionUtils.createSave_With_Date(member, cycle, newQuestion, yesterday);

            UpdateAnswerRequest request = new UpdateAnswerRequest("답변 없는 날짜", null);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.ANSWER_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("DailyQuestion이 없는 날짜로 수정 요청 시 404 응답")
        void update_answer_for_nonexistent_date_returns_404() throws Exception {
            // given
            LocalDate noQuestionDate = today.minusDays(10);
            UpdateAnswerRequest request = new UpdateAnswerRequest("없는 날짜", null);

            // when & then
            mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}/answer", noQuestionDate)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }
    }
}
