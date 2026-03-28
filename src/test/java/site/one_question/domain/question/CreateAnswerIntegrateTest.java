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
import org.springframework.http.MediaType;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.answerpost.domain.AnswerPostStatus;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionExceptionSpec;
import site.one_question.api.question.presentation.request.CreateAnswerRequest;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.test_config.IntegrateTest;

import java.time.LocalDate;
import java.time.ZoneId;

@DisplayName("답변 생성 통합 테스트")
class CreateAnswerIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;
    private DailyQuestion dailyQuestion;
    private LocalDate today;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        today = LocalDate.now(ZoneId.of(TIMEZONE));
        dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("답변 생성 시 200 응답 및 답변 정보 반환")
        void create_answer_returns_200_ok() throws Exception {
            // given
            CreateAnswerRequest request = new CreateAnswerRequest("새로운 시작의 날", false);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyAnswerId").exists())
                    .andExpect(jsonPath("$.content").value("새로운 시작의 날"))
                    .andExpect(jsonPath("$.answeredAt").exists())
                    .andExpect(jsonPath("$.published").value(false));

            // DB 검증
            assertThat(dailyQuestionAnswerRepository.findAll())
                    .as("DailyQuestionAnswer가 1개 생성되어야 함")
                    .hasSize(1);
            assertThat(answerPostRepository.findAll())
                    .as("publish=false이므로 AnswerPost가 생성되지 않아야 함")
                    .isEmpty();
        }

        @Test
        @DisplayName("publish=true로 답변 생성 시 AnswerPost도 동시에 생성")
        void create_answer_with_publish_true_creates_answer_post() throws Exception {
            // given
            CreateAnswerRequest request = new CreateAnswerRequest("공개할 답변", true);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyAnswerId").exists())
                    .andExpect(jsonPath("$.content").value("공개할 답변"))
                    .andExpect(jsonPath("$.published").value(true));

            // DB 검증 - DailyQuestionAnswer 생성 확인
            assertThat(dailyQuestionAnswerRepository.findAll())
                    .as("DailyQuestionAnswer가 1개 생성되어야 함")
                    .hasSize(1);

            // DB 검증 - AnswerPost 생성 확인
            assertThat(answerPostRepository.findAll())
                    .as("publish=true이므로 AnswerPost가 1개 생성되어야 함")
                    .hasSize(1);

            AnswerPost savedPost = answerPostRepository.findAll().get(0);
            assertThat(savedPost.getStatus())
                    .as("게시 상태가 PUBLISHED여야 함")
                    .isEqualTo(AnswerPostStatus.PUBLISHED);
            assertThat(savedPost.getMember().getId())
                    .as("작성자 ID가 일치해야 함")
                    .isEqualTo(member.getId());
            assertThat(savedPost.getAnonymousNickname())
                    .as("익명 닉네임이 생성되어야 함")
                    .isNotBlank();
        }

        @Test
        @DisplayName("publish 필드 미전송 시 기본값 false로 동작")
        void create_answer_without_publish_field_defaults_to_false() throws Exception {
            // given - publish 필드 없이 기존 형식으로 요청
            String requestBody = """
                    { "answer": "기본값 테스트" }
                    """;

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.published").value(false));

            // DB 검증
            assertThat(answerPostRepository.findAll())
                    .as("publish 미전송 시 AnswerPost가 생성되지 않아야 함")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("이미 답변이 존재하는 경우 409 응답")
        void create_answer_when_already_exists_returns_409() throws Exception {
            // given - 이미 답변 존재
            testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);
            CreateAnswerRequest request = new CreateAnswerRequest("중복 답변", false);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", today)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.ANSWER_ALREADY_EXISTS.getCode()));
        }

        @Test
        @DisplayName("존재하지 않는 날짜로 답변 생성 시 404 응답")
        void create_answer_for_nonexistent_date_returns_404() throws Exception {
            // given - DailyQuestion이 없는 날짜
            LocalDate yesterday = today.minusDays(1);
            CreateAnswerRequest request = new CreateAnswerRequest("없는 날짜 답변", false);

            // when & then
            mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", yesterday)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(QuestionExceptionSpec.DAILY_QUESTION_NOT_FOUND.getCode()));
        }
    }
}
