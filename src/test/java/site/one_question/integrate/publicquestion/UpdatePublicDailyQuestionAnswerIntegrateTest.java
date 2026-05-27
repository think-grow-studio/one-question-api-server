package site.one_question.integrate.publicquestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
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
import site.one_question.api.publicquestion.presentation.request.UpdatePublicDailyQuestionAnswerRequest;
import site.one_question.api.question.domain.Question;
import site.one_question.common.HttpHeaderConstant;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 답변 수정 통합 테스트")
class UpdatePublicDailyQuestionAnswerIntegrateTest extends IntegrateTest {

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
        return objectMapper.writeValueAsString(new UpdatePublicDailyQuestionAnswerRequest(content));
    }

    private PublicDailyQuestionAnswer saveAnswer(Member owner, String content) {
        return publicDailyQuestionAnswerRepository.save(
                PublicDailyQuestionAnswer.create(pdq, owner, content, TIMEZONE));
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("정상 답변 수정 시 응답 200 + DB content 갱신 검증")
        void updates_answer_successfully() throws Exception {
            PublicDailyQuestionAnswer answer = saveAnswer(member, "원래 답변");
            String newContent = "수정된 답변 내용";

            mockMvc.perform(patch(API + "/{pdqId}/answers/{answerId}", pdq.getId(), answer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(newContent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicDailyQuestionAnswerId").value(answer.getId()))
                    .andExpect(jsonPath("$.content").value(newContent))
                    .andExpect(jsonPath("$.anonymousNickname").value(answer.getAnonymousNickname()))
                    .andExpect(jsonPath("$.answeredAt").exists());

            PublicDailyQuestionAnswer updated = publicDailyQuestionAnswerRepository.findById(answer.getId()).orElseThrow();
            assertThat(updated.getContent()).isEqualTo(newContent);
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("다른 멤버의 답변을 수정 시도하면 404 반환")
        void returns_404_when_updating_other_member_answer() throws Exception {
            Member other = testMemberUtils.createSave();
            PublicDailyQuestionAnswer otherAnswer = saveAnswer(other, "다른 멤버 답변");

            mockMvc.perform(patch(API + "/{pdqId}/answers/{answerId}", pdq.getId(), otherAnswer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("수정 내용")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("answerId가 path의 pdqId에 속하지 않으면 404 반환")
        void returns_404_when_answer_does_not_belong_to_pdq() throws Exception {
            PublicDailyQuestionAnswer myAnswer = saveAnswer(member, "원래 답변");

            // 다른 PDQ 생성
            Question otherQuestion = testQuestionUtils.createSave();
            LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
            PublicDailyQuestion otherPdq = publicDailyQuestionRepository.save(
                    PublicDailyQuestion.publish(otherQuestion, tomorrow));

            // myAnswer 는 pdq 소속인데, otherPdq 의 하위로 수정 시도
            mockMvc.perform(patch(API + "/{pdqId}/answers/{answerId}", otherPdq.getId(), myAnswer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("수정 내용")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND.getCode()));
        }
    }
}
