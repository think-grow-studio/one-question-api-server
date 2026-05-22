package site.one_question.integrate.publicquestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
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
import site.one_question.api.question.domain.Question;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("공개 일일 질문 답변 목록 조회 통합 테스트")
class GetPublicDailyQuestionAnswersIntegrateTest extends IntegrateTest {

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

    private PublicDailyQuestionAnswer saveAnswerAt(Member owner, String content, Instant answeredAt) {
        PublicDailyQuestionAnswer answer = PublicDailyQuestionAnswer.create(pdq, owner, content, TIMEZONE);
        publicDailyQuestionAnswerRepository.save(answer);
        // answeredAt 은 create 시 Instant.now() 로 박혀있어 정확한 시간 제어가 어려움.
        // 통합 테스트에서는 native query 로 강제 갱신해서 정렬 가시성을 확보.
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("UPDATE public_daily_question_answer SET answered_at = ?1 WHERE id = ?2")
                    .setParameter(1, answeredAt)
                    .setParameter(2, answer.getId())
                    .executeUpdate();
        });
        return publicDailyQuestionAnswerRepository.findById(answer.getId()).orElseThrow();
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("본인 답변은 목록에서 제외되고, 최신순(answeredAt DESC)으로 정렬됨")
        void excludes_own_answer_and_orders_by_answered_at_desc() throws Exception {
            Instant base = Instant.now();
            Member otherA = testMemberUtils.createSave();
            Member otherB = testMemberUtils.createSave();

            saveAnswerAt(member, "내 답변", base.minusSeconds(30));   // 본인 — 제외 대상
            PublicDailyQuestionAnswer olderOther = saveAnswerAt(otherA, "오래된 답변", base.minusSeconds(60));
            PublicDailyQuestionAnswer newerOther = saveAnswerAt(otherB, "최신 답변", base.minusSeconds(10));

            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(newerOther.getId()))
                    .andExpect(jsonPath("$.items[1].publicDailyQuestionAnswerId").value(olderOther.getId()))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        @DisplayName("size 보다 항목 많으면 hasNext=true 와 nextCursor 반환")
        void returns_cursor_when_more_pages_exist() throws Exception {
            Instant base = Instant.now();
            saveAnswerAt(testMemberUtils.createSave(), "1", base.minusSeconds(10));
            saveAnswerAt(testMemberUtils.createSave(), "2", base.minusSeconds(20));
            saveAnswerAt(testMemberUtils.createSave(), "3", base.minusSeconds(30));

            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor.answeredAt").exists())
                    .andExpect(jsonPath("$.nextCursor.id").exists());
        }

        @Test
        @DisplayName("cursor 이후 항목만 반환 (다음 페이지 시나리오)")
        void returns_next_page_after_cursor() throws Exception {
            Instant base = Instant.now();
            PublicDailyQuestionAnswer a1 = saveAnswerAt(testMemberUtils.createSave(), "최신", base.minusSeconds(10));
            PublicDailyQuestionAnswer a2 = saveAnswerAt(testMemberUtils.createSave(), "중간", base.minusSeconds(20));
            PublicDailyQuestionAnswer a3 = saveAnswerAt(testMemberUtils.createSave(), "오래", base.minusSeconds(30));

            // cursor 를 a1 기준으로 → a2, a3 만 반환되어야 함
            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .param("cursorAnsweredAt", a1.getAnsweredAt().toString())
                            .param("cursorId", a1.getId().toString())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(a2.getId()))
                    .andExpect(jsonPath("$.items[1].publicDailyQuestionAnswerId").value(a3.getId()))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("answeredAt 동률 시 id DESC 로 정렬")
        void breaks_tie_by_id_desc() throws Exception {
            Instant sameTime = Instant.now().minusSeconds(60);
            PublicDailyQuestionAnswer first = saveAnswerAt(testMemberUtils.createSave(), "A", sameTime);
            PublicDailyQuestionAnswer second = saveAnswerAt(testMemberUtils.createSave(), "B", sameTime);  // 더 큰 id

            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(second.getId()))
                    .andExpect(jsonPath("$.items[1].publicDailyQuestionAnswerId").value(first.getId()));
        }

        @Test
        @DisplayName("각 항목의 likeCount/liked 가 배치 조회되어 정확히 반환")
        void returns_like_info_per_item() throws Exception {
            Instant base = Instant.now();
            Member otherA = testMemberUtils.createSave();
            Member otherB = testMemberUtils.createSave();
            PublicDailyQuestionAnswer answerA = saveAnswerAt(otherA, "A", base.minusSeconds(10));
            PublicDailyQuestionAnswer answerB = saveAnswerAt(otherB, "B", base.minusSeconds(20));

            // answerA: 본인 + otherB 둘 다 좋아요 (count=2, liked=true)
            publicDailyQuestionAnswerLikeRepository.save(PublicDailyQuestionAnswerLike.create(answerA, member));
            publicDailyQuestionAnswerLikeRepository.save(PublicDailyQuestionAnswerLike.create(answerA, otherB));
            // answerB: otherA 만 좋아요 (count=1, liked=false)
            publicDailyQuestionAnswerLikeRepository.save(PublicDailyQuestionAnswerLike.create(answerB, otherA));

            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(answerA.getId()))
                    .andExpect(jsonPath("$.items[0].likeCount").value(2))
                    .andExpect(jsonPath("$.items[0].liked").value(true))
                    .andExpect(jsonPath("$.items[1].publicDailyQuestionAnswerId").value(answerB.getId()))
                    .andExpect(jsonPath("$.items[1].likeCount").value(1))
                    .andExpect(jsonPath("$.items[1].liked").value(false));
        }

        @Test
        @DisplayName("답변이 없을 때 빈 목록과 hasNext=false 반환")
        void returns_empty_when_no_answers() throws Exception {
            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        @DisplayName("응답의 nextCursor 를 재사용한 무한 스크롤 체인이 끊김/중복 없이 이어진다")
        void infinite_scroll_chain_works_end_to_end() throws Exception {
            // given - 5건의 답변, size=2 로 3페이지 시나리오
            Instant base = Instant.now();
            PublicDailyQuestionAnswer a1 = saveAnswerAt(testMemberUtils.createSave(), "1", base.minusSeconds(10));
            PublicDailyQuestionAnswer a2 = saveAnswerAt(testMemberUtils.createSave(), "2", base.minusSeconds(20));
            PublicDailyQuestionAnswer a3 = saveAnswerAt(testMemberUtils.createSave(), "3", base.minusSeconds(30));
            PublicDailyQuestionAnswer a4 = saveAnswerAt(testMemberUtils.createSave(), "4", base.minusSeconds(40));
            PublicDailyQuestionAnswer a5 = saveAnswerAt(testMemberUtils.createSave(), "5", base.minusSeconds(50));

            // page 1
            String page1Json = mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(a1.getId()))
                    .andExpect(jsonPath("$.items[1].publicDailyQuestionAnswerId").value(a2.getId()))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            JsonNode page1Cursor = objectMapper.readTree(page1Json).get("nextCursor");

            // page 2 — page1 의 nextCursor 를 그대로 사용
            String page2Json = mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .param("size", "2")
                            .param("cursorAnsweredAt", page1Cursor.get("answeredAt").asText())
                            .param("cursorId", page1Cursor.get("id").asText())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(a3.getId()))
                    .andExpect(jsonPath("$.items[1].publicDailyQuestionAnswerId").value(a4.getId()))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            JsonNode page2Cursor = objectMapper.readTree(page2Json).get("nextCursor");

            // page 3 — 마지막 항목 + hasNext=false
            mockMvc.perform(get(API + "/{pdqId}/answers", pdq.getId())
                            .param("size", "2")
                            .param("cursorAnsweredAt", page2Cursor.get("answeredAt").asText())
                            .param("cursorId", page2Cursor.get("id").asText())
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].publicDailyQuestionAnswerId").value(a5.getId()))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }
    }

    @Nested
    @DisplayName("예외 케이스")
    class ExceptionTest {

        @Test
        @DisplayName("존재하지 않는 pdqId 요청 시 404 반환")
        void returns_404_when_pdq_not_found() throws Exception {
            mockMvc.perform(get(API + "/{pdqId}/answers", 999999L)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound());
        }
    }
}
