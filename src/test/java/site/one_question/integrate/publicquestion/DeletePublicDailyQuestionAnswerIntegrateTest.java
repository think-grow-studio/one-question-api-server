package site.one_question.integrate.publicquestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerLike;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerLikeRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.publicquestion.domain.exception.PublicQuestionExceptionSpec;
import site.one_question.api.question.domain.Question;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 답변 삭제 통합 테스트")
class DeletePublicDailyQuestionAnswerIntegrateTest extends IntegrateTest {

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
    private PublicDailyQuestion pdq;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        Question question = testQuestionUtils.createSave();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        pdq = publicDailyQuestionRepository.save(PublicDailyQuestion.publish(question, today));
    }

    private PublicDailyQuestionAnswer saveAnswer(Member owner, String content) {
        return publicDailyQuestionAnswerRepository.save(
                PublicDailyQuestionAnswer.create(pdq, owner, content, TIMEZONE));
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("본인 답변 삭제 시 204 반환 + DB 에서 답변과 좋아요 함께 제거")
        void deletes_own_answer_with_likes() throws Exception {
            PublicDailyQuestionAnswer answer = saveAnswer(member, "내 답변");
            Member liker = testMemberUtils.createSave();
            publicDailyQuestionAnswerLikeRepository.save(PublicDailyQuestionAnswerLike.create(answer, member));
            publicDailyQuestionAnswerLikeRepository.save(PublicDailyQuestionAnswerLike.create(answer, liker));

            mockMvc.perform(delete(API + "/{pdqId}/answers/{answerId}", pdq.getId(), answer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNoContent());

            assertThat(publicDailyQuestionAnswerRepository.findById(answer.getId())).isEmpty();
            assertThat(publicDailyQuestionAnswerLikeRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("다른 멤버의 답변을 삭제 시도하면 404 반환 + DB 에 그대로 유지")
        void returns_404_when_deleting_other_member_answer() throws Exception {
            Member other = testMemberUtils.createSave();
            PublicDailyQuestionAnswer otherAnswer = saveAnswer(other, "다른 멤버 답변");

            mockMvc.perform(delete(API + "/{pdqId}/answers/{answerId}", pdq.getId(), otherAnswer.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND.getCode()));

            assertThat(publicDailyQuestionAnswerRepository.findById(otherAnswer.getId())).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 answerId 요청 시 404 반환")
        void returns_404_when_answer_not_found() throws Exception {
            long nonExistentAnswerId = 999999L;
            mockMvc.perform(delete(API + "/{pdqId}/answers/{answerId}", pdq.getId(), nonExistentAnswerId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PUBLIC_ANSWER_NOT_FOUND.getCode()));
        }
    }
}
