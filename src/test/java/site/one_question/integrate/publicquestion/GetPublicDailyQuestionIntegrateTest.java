package site.one_question.integrate.publicquestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.publicquestion.domain.exception.PublicQuestionExceptionSpec;
import site.one_question.api.question.domain.Question;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 조회 통합 테스트")
class GetPublicDailyQuestionIntegrateTest extends IntegrateTest {

    private static final String API = PUBLIC_QUESTIONS_API + "/daily";

    @Autowired
    private PublicDailyQuestionRepository publicDailyQuestionRepository;

    private Member member;
    private String token;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("존재하는 날짜 요청 시 200 반환")
        void returns_200_when_pdq_exists() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Question question = testQuestionUtils.createSave();
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(question, today));

            // when & then
            mockMvc.perform(get(API + "/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("응답 필드가 정확하게 반환됨")
        void returns_correct_response_fields() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Question question = testQuestionUtils.createSave_With_Content("오늘의 공개 질문");
            PublicDailyQuestion pdq = publicDailyQuestionRepository.save(PublicDailyQuestion.publish(question, today));

            // when & then
            mockMvc.perform(get(API + "/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicDailyQuestionId").value(pdq.getId()))
                    .andExpect(jsonPath("$.questionId").value(question.getId()))
                    .andExpect(jsonPath("$.content").value("오늘의 공개 질문"))
                    .andExpect(jsonPath("$.questionDate").value(today.toString()));
        }

        @Test
        @DisplayName("멤버 locale(ko-KR)에 맞는 PDQ 반환")
        void returns_pdq_matching_member_locale() throws Exception {
            // given - ko-KR PDQ 와 en-US PDQ 모두 존재
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Question koQuestion = testQuestionUtils.createSave();
            Question enQuestion = testQuestionUtils.createSave_With_Locale("en-US");
            PublicDailyQuestion koPdq = publicDailyQuestionRepository.save(PublicDailyQuestion.publish(koQuestion, today));
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(enQuestion, today));

            // when & then - ko-KR 멤버는 ko-KR PDQ 반환
            mockMvc.perform(get(API + "/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicDailyQuestionId").value(koPdq.getId()));
        }

        @Test
        @DisplayName("en-US 멤버는 en-US PDQ 반환")
        void returns_en_pdq_for_en_member() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Member enMember = testMemberUtils.createSave_With_Locale("en-US");
            String enToken = testAuthUtils.createBearerToken(enMember);

            Question koQuestion = testQuestionUtils.createSave();
            Question enQuestion = testQuestionUtils.createSave_With_Locale("en-US");
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(koQuestion, today));
            PublicDailyQuestion enPdq = publicDailyQuestionRepository.save(PublicDailyQuestion.publish(enQuestion, today));

            // when & then
            mockMvc.perform(get(API + "/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, enToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicDailyQuestionId").value(enPdq.getId()));
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("해당 날짜 PDQ 없으면 404 반환")
        void returns_404_when_pdq_not_found() throws Exception {
            // given - PDQ 없음
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            // when & then
            mockMvc.perform(get(API + "/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PDQ_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("멤버 locale에 맞는 PDQ 없으면 404 반환")
        void returns_404_when_no_pdq_for_member_locale() throws Exception {
            // given - en-US PDQ만 존재, ko-KR 멤버 요청
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            Question enQuestion = testQuestionUtils.createSave_With_Locale("en-US");
            publicDailyQuestionRepository.save(PublicDailyQuestion.publish(enQuestion, today));

            // when & then
            mockMvc.perform(get(API + "/{date}", today)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(PublicQuestionExceptionSpec.PDQ_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("인증 헤더 없으면 401 반환")
        void returns_401_when_no_auth() throws Exception {
            // given
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            // when & then
            mockMvc.perform(get(API + "/{date}", today))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 날짜 형식 요청 시 400 반환")
        void returns_400_when_invalid_date_format() throws Exception {
            // when & then
            mockMvc.perform(get(API + "/invalid-date")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest());
        }
    }
}
