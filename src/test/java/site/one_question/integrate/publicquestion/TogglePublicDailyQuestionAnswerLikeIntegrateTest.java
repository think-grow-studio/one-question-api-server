package site.one_question.integrate.publicquestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import site.one_question.api.member.domain.Member;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerLikeRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.publicquestion.domain.exception.PublicQuestionExceptionSpec;
import site.one_question.api.question.domain.Question;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 답변 좋아요 토글 통합 테스트")
class TogglePublicDailyQuestionAnswerLikeIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";
    private static final String API = PUBLIC_QUESTIONS_API;

    @Autowired
    private PublicDailyQuestionRepository publicDailyQuestionRepository;

    @Autowired
    private PublicDailyQuestionAnswerRepository publicDailyQuestionAnswerRepository;

    @Autowired
    private PublicDailyQuestionAnswerLikeRepository publicDailyQuestionAnswerLikeRepository;

    private Member member;
    private String token;
    private PublicDailyQuestionAnswer answer;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        Question question = testQuestionUtils.createSave();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PublicDailyQuestion pdq = publicDailyQuestionRepository.save(PublicDailyQuestion.publish(question, today));
        Member author = testMemberUtils.createSave();
        answer = publicDailyQuestionAnswerRepository.save(
                PublicDailyQuestionAnswer.create(pdq, author, "다른 멤버의 답변", TIMEZONE));
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("좋아요 → 취소 토글 시 응답과 DB 상태 일관성 검증")
        void toggles_like_on_and_off() throws Exception {
            // 1st call: like ON
            mockMvc.perform(post(API + "/answers/{answerId}/like", answer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(true));

            assertThat(publicDailyQuestionAnswerLikeRepository.findAll()).hasSize(1);

            // 2nd call: like OFF
            mockMvc.perform(post(API + "/answers/{answerId}/like", answer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false));

            assertThat(publicDailyQuestionAnswerLikeRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("존재하지 않는 answerId 요청 시 404 반환")
        void returns_404_when_answer_not_found() throws Exception {
            long nonExistentAnswerId = 999999L;
            mockMvc.perform(post(API + "/answers/{answerId}/like", nonExistentAnswerId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND.getCode()));
        }
    }
}
