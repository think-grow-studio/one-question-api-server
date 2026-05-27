package site.one_question.integrate.publicquestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import site.one_question.api.member.domain.Member;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.publicquestion.domain.exception.PublicQuestionExceptionSpec;
import site.one_question.api.publicquestion.presentation.request.CreatePublicDailyQuestionAnswerRequest;
import site.one_question.api.question.domain.Question;
import site.one_question.common.HttpHeaderConstant;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 답변 작성 통합 테스트")
class CreatePublicDailyQuestionAnswerIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";
    private static final String API = PUBLIC_QUESTIONS_API;

    @Autowired
    private PublicDailyQuestionRepository publicDailyQuestionRepository;

    @Autowired
    private PublicDailyQuestionAnswerRepository publicDailyQuestionAnswerRepository;

    private Member member;
    private String token;
    private PublicDailyQuestion pdq;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        Question question = testQuestionUtils.createSave();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        pdq = publicDailyQuestionRepository.save(PublicDailyQuestion.publish(question, today));
    }

    private String body(String content) throws Exception {
        return objectMapper.writeValueAsString(new CreatePublicDailyQuestionAnswerRequest(content));
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("정상 답변 작성 시 응답 200 + DB 저장 검증")
        void creates_answer_successfully() throws Exception {
            String content = "오늘 가장 인상 깊었던 일";

            mockMvc.perform(post(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(content)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicDailyQuestionAnswerId").exists())
                    .andExpect(jsonPath("$.content").value(content))
                    .andExpect(jsonPath("$.anonymousNickname").exists())
                    .andExpect(jsonPath("$.answeredAt").exists());

            List<PublicDailyQuestionAnswer> answers = publicDailyQuestionAnswerRepository.findAll();
            assertThat(answers).hasSize(1);
            assertThat(answers.get(0).getContent()).isEqualTo(content);
            assertThat(answers.get(0).getAnonymousNickname()).isNotBlank();
            assertThat(answers.get(0).getTimezone()).isEqualTo(TIMEZONE);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("이미 답변한 PDQ 에 재답변 시 409 반환")
        void returns_409_when_already_answered() throws Exception {
            // 첫 답변
            mockMvc.perform(post(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("첫 답변")))
                    .andExpect(status().isOk());

            // 두 번째 답변 - 409
            mockMvc.perform(post(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("두 번째 답변")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PUBLIC_ANSWER_ALREADY_EXISTS.getCode()));
        }

        @Test
        @DisplayName("존재하지 않는 pdqId 요청 시 404 반환")
        void returns_404_when_pdq_not_found() throws Exception {
            long nonExistentPdqId = 999999L;
            mockMvc.perform(post(API + "/{pdqId}/answers", nonExistentPdqId)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("답변")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PDQ_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("빈 content 요청 시 400 반환")
        void returns_400_when_content_empty() throws Exception {
            mockMvc.perform(post(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.EMPTY_ANSWER_CONTENT.getCode()));
        }

        @Test
        @DisplayName("content 길이 초과 시 400 반환")
        void returns_400_when_content_too_long() throws Exception {
            String tooLong = "a".repeat(3001);
            mockMvc.perform(post(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(tooLong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.ANSWER_CONTENT_TOO_LONG.getCode()));
        }

        @Test
        @DisplayName("인증 헤더 없으면 401 반환")
        void returns_401_when_no_auth() throws Exception {
            mockMvc.perform(post(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("답변")))
                    .andExpect(status().isUnauthorized());
        }
    }
}
