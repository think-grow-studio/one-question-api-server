package site.one_question.domain.answerpost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import site.one_question.api.answerpost.domain.AnswerPost;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.global.common.HttpHeaderConstant;
import site.one_question.test_config.IntegrateTest;

@DisplayName("공개 답변 피드 조회 통합 테스트")
class GetAnswerPostFeedIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;
    private QuestionCycle cycle;
    private int dateOffset;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        cycle = testQuestionCycleUtils.createSave(member);
        dateOffset = 0;
    }

    private LocalDate nextDate() {
        return LocalDate.now(ZoneId.of(TIMEZONE)).minusDays(++dateOffset);
    }

    private AnswerPost createAnswerPostWithPostedAt(Member postMember, QuestionCycle postCycle, Instant postedAt) {
        Question question = testQuestionUtils.createSave();
        DailyQuestion dq = testDailyQuestionUtils.createSave_With_Date(postMember, postCycle, question, nextDate());
        DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave(dq, postMember);
        return testAnswerPostUtils.createSave_With_PostedAt(answer, postMember, postedAt);
    }

    private AnswerPost createAnswerPost(Member postMember, QuestionCycle postCycle) {
        Question question = testQuestionUtils.createSave();
        DailyQuestion dq = testDailyQuestionUtils.createSave_With_Date(postMember, postCycle, question, nextDate());
        DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave(dq, postMember);
        return testAnswerPostUtils.createSave(answer, postMember);
    }

    private AnswerPost createUnpublishedAnswerPost(Member postMember, QuestionCycle postCycle) {
        Question question = testQuestionUtils.createSave();
        DailyQuestion dq = testDailyQuestionUtils.createSave_With_Date(postMember, postCycle, question, nextDate());
        DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave(dq, postMember);
        return testAnswerPostUtils.createSave_Unpublished(answer, postMember);
    }

    @Nested
    @DisplayName("정상 케이스")
    class SuccessTest {

        @Test
        @DisplayName("피드 조회 시 최신순으로 공개된 답변 반환")
        void get_feed_returns_published_posts_in_descending_order() throws Exception {
            // given
            Instant base = Instant.now().minus(3, ChronoUnit.HOURS);
            AnswerPost oldest = createAnswerPostWithPostedAt(member, cycle, base);
            AnswerPost middle = createAnswerPostWithPostedAt(member, cycle, base.plus(1, ChronoUnit.HOURS));
            AnswerPost newest = createAnswerPostWithPostedAt(member, cycle, base.plus(2, ChronoUnit.HOURS));

            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[0].answerPostId").value(newest.getId()))
                    .andExpect(jsonPath("$.items[1].answerPostId").value(middle.getId()))
                    .andExpect(jsonPath("$.items[2].answerPostId").value(oldest.getId()));
        }

        @Test
        @DisplayName("피드에 게시 취소된 답변 미포함")
        void get_feed_excludes_unpublished_posts() throws Exception {
            // given
            createAnswerPost(member, cycle);
            createAnswerPost(member, cycle);
            createUnpublishedAnswerPost(member, cycle);

            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2));
        }

        @Test
        @DisplayName("피드 아이템에 좋아요 수와 좋아요 여부 포함")
        void get_feed_includes_like_count_and_liked_status() throws Exception {
            // given
            AnswerPost post = createAnswerPost(member, cycle);

            Member otherMember = testMemberUtils.createSave();
            testAnswerPostLikeUtils.createSave(post, member);
            testAnswerPostLikeUtils.createSave(post, otherMember);

            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].likeCount").value(2))
                    .andExpect(jsonPath("$.items[0].liked").value(true));
        }

        @Test
        @DisplayName("피드 아이템에 본인 작성 여부 포함")
        void get_feed_includes_mine_flag() throws Exception {
            // given
            Instant base = Instant.now().minus(2, ChronoUnit.HOURS);
            Member otherMember = testMemberUtils.createSave();
            QuestionCycle otherCycle = testQuestionCycleUtils.createSave(otherMember);

            createAnswerPostWithPostedAt(otherMember, otherCycle, base);
            createAnswerPostWithPostedAt(member, cycle, base.plus(1, ChronoUnit.HOURS));

            // when & then - member가 조회
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].mine").value(true))
                    .andExpect(jsonPath("$.items[1].mine").value(false));
        }

        @Test
        @DisplayName("피드 아이템에 질문 내용과 답변 내용 포함")
        void get_feed_returns_question_and_answer_content() throws Exception {
            // given
            Question question = testQuestionUtils.createSave_With_Content("오늘의 질문입니다");
            DailyQuestion dq = testDailyQuestionUtils.createSave(member, cycle, question);
            DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave_With_Content(
                    dq, member, "나의 답변입니다");
            testAnswerPostUtils.createSave(answer, member);

            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].questionContent").value("오늘의 질문입니다"))
                    .andExpect(jsonPath("$.items[0].answerContent").value("나의 답변입니다"));
        }

        @Test
        @DisplayName("커서 없이 조회 시 최신 게시물부터 반환")
        void get_feed_without_cursor_returns_latest_posts() throws Exception {
            // given
            createAnswerPost(member, cycle);

            // when & then - 커서 파라미터 없이 요청
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1));
        }

        @Test
        @DisplayName("게시물이 없을 때 빈 피드 반환")
        void get_feed_returns_empty_when_no_posts() throws Exception {
            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }
    }

    @Nested
    @DisplayName("페이지네이션 테스트")
    class PaginationTest {

        @Test
        @DisplayName("size 파라미터로 결과 수 제한")
        void get_feed_with_size_limits_results() throws Exception {
            // given - 5개 게시물 생성
            Instant base = Instant.now().minus(6, ChronoUnit.HOURS);
            for (int i = 0; i < 5; i++) {
                createAnswerPostWithPostedAt(member, cycle, base.plus(i, ChronoUnit.HOURS));
            }

            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.nextCursor").exists());
        }

        @Test
        @DisplayName("더 이상 게시물이 없으면 hasNext=false")
        void get_feed_has_next_false_when_no_more_posts() throws Exception {
            // given - 2개 게시물
            Instant base = Instant.now().minus(3, ChronoUnit.HOURS);
            createAnswerPostWithPostedAt(member, cycle, base);
            createAnswerPostWithPostedAt(member, cycle, base.plus(1, ChronoUnit.HOURS));

            // when & then
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.nextCursor").doesNotExist());
        }

        @Test
        @DisplayName("커서로 다음 페이지 조회 시 겹침 없음")
        void get_feed_cursor_returns_next_page() throws Exception {
            // given - 5개 게시물 (시간 간격을 두고 생성)
            Instant base = Instant.now().minus(6, ChronoUnit.HOURS);
            List<AnswerPost> posts = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                posts.add(createAnswerPostWithPostedAt(member, cycle, base.plus(i, ChronoUnit.HOURS)));
            }

            // when - 첫 페이지 (size=3)
            MvcResult firstPage = mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andReturn();

            // nextCursor 추출
            String responseJson = firstPage.getResponse().getContentAsString();
            String nextCursor = objectMapper.readTree(responseJson).get("nextCursor").asText();

            // when - 두 번째 페이지
            mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .param("cursor", nextCursor)
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("전체 피드를 페이지네이션으로 완전 순회")
        void get_feed_pagination_complete_traversal() throws Exception {
            // given - 7개 게시물
            Instant base = Instant.now().minus(8, ChronoUnit.HOURS);
            for (int i = 0; i < 7; i++) {
                createAnswerPostWithPostedAt(member, cycle, base.plus(i, ChronoUnit.HOURS));
            }

            int totalItems = 0;
            String cursor = null;

            // 첫 페이지
            MvcResult result = mockMvc.perform(get(ANSWER_POSTS_API)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andReturn();

            var tree = objectMapper.readTree(result.getResponse().getContentAsString());
            totalItems += tree.get("items").size();
            boolean hasNext = tree.get("hasNext").asBoolean();

            // 남은 페이지 순회
            while (hasNext) {
                cursor = tree.get("nextCursor").asText();
                result = mockMvc.perform(get(ANSWER_POSTS_API)
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
                                .param("cursor", cursor)
                                .param("size", "3"))
                        .andExpect(status().isOk())
                        .andReturn();

                tree = objectMapper.readTree(result.getResponse().getContentAsString());
                totalItems += tree.get("items").size();
                hasNext = tree.get("hasNext").asBoolean();
            }

            // then - 총 7개 아이템, 마지막은 hasNext=false
            assertThat(totalItems)
                    .as("전체 순회 시 총 7개 아이템이 조회되어야 함")
                    .isEqualTo(7);
            assertThat(hasNext)
                    .as("마지막 페이지의 hasNext는 false여야 함")
                    .isFalse();
        }
    }
}
