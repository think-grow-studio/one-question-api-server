package site.one_question.integrate.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import site.one_question.api.member.domain.Member;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswer;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerLike;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerLikeRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionAnswerRepository;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionRepository;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.presentation.request.CreateAnswerRequest;
import site.one_question.common.HttpHeaderConstant;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("회원 탈퇴 API 통합 테스트")
class AuthWithdrawIntegrateTest extends IntegrateTest {

  private static final String TIMEZONE = "Asia/Seoul";

  @Autowired
  private PublicDailyQuestionRepository publicDailyQuestionRepository;

  @Autowired
  private PublicDailyQuestionAnswerRepository publicDailyQuestionAnswerRepository;

  @Autowired
  private PublicDailyQuestionAnswerLikeRepository publicDailyQuestionAnswerLikeRepository;

  private Member member;
  private String token;

  @BeforeEach
  void setUp() {
    member = testMemberUtils.createSave();
    token = testAuthUtils.createBearerToken(member);
    testRefreshTokenUtils.createSave_Valid(member, "refresh-token");

    QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
    Question question = testQuestionUtils.createSave();
    DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
    testDailyQuestionAnswerUtils.createSave(dailyQuestion, member);
  }

  @Test
  @DisplayName("회원 탈퇴 성공 시 계정 삭제 및 리프레시 토큰 삭제")
  void withdraw_successfully_deletes_member_and_refresh_token() throws Exception {
    // when & then
    mockMvc.perform(delete(AUTH_API + "/me")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNoContent());

    assertThat(memberRepository.findById(member.getId()))
        .as("탈퇴 후 회원 정보가 삭제되어야 함")
        .isEmpty();

    assertThat(refreshTokenRepository.findByMember_Id(member.getId()))
        .as("탈퇴 후 리프레시 토큰이 삭제되어야 함")
        .isEmpty();
  }

  @Test
  @DisplayName("질문 API를 3일 사용한 뒤 탈퇴해도 질문 관련 데이터가 모두 삭제된다")
  void withdraw_after_three_day_question_api_scenario_deletes_question_data() throws Exception {
    LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
    LocalDate yesterday = today.minusDays(1);
    LocalDate twoDaysAgo = today.minusDays(2);
    List<LocalDate> scenarioDates = List.of(twoDaysAgo, yesterday, today);

    Member scenarioMember = testMemberUtils.createSave_With_JoinedDate(twoDaysAgo);
    String scenarioToken = testAuthUtils.createBearerToken(scenarioMember);
    testRefreshTokenUtils.createSave_Valid(scenarioMember, "scenario-refresh-token");
    QuestionCycle scenarioCycle =
        testQuestionCycleUtils.createSave_With_StartDate(scenarioMember, twoDaysAgo, TIMEZONE, 1);

    for (int i = 0; i < 6; i++) {
      testQuestionUtils.createSave();
    }

    for (LocalDate date : scenarioDates) {
      String serveResponse = mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", date)
              .header(HttpHeaders.AUTHORIZATION, scenarioToken)
              .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
      long initialQuestionId = objectMapper.readTree(serveResponse).get("questionId").asLong();
      String reloadResponse = mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/reload", date)
              .header(HttpHeaders.AUTHORIZATION, scenarioToken)
              .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
      long reloadedQuestionId = objectMapper.readTree(reloadResponse).get("questionId").asLong();

      mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/candidates/cycle-check", date)
              .header(HttpHeaders.AUTHORIZATION, scenarioToken)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(Map.of("questionId", reloadedQuestionId))))
          .andExpect(status().isOk());

      mockMvc.perform(patch(QUESTIONS_API + "/daily/{date}", date)
              .header(HttpHeaders.AUTHORIZATION, scenarioToken)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(Map.of("questionId", initialQuestionId))))
          .andExpect(status().isOk());
    }

    String answerResponse = mockMvc.perform(post(QUESTIONS_API + "/daily/{date}/answer", today)
            .header(HttpHeaders.AUTHORIZATION, scenarioToken)
            .header(HttpHeaderConstant.TIMEZONE, TIMEZONE)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CreateAnswerRequest("탈퇴 전 답변", false))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    long answerId = objectMapper.readTree(answerResponse).get("dailyAnswerId").asLong();

    assertThat(questionCycleRepository.findByMemberIdOrderByCycleNumberDesc(scenarioMember.getId()))
        .as("탈퇴 전에는 질문 사이클이 존재해야 함")
        .hasSize(1);
    List<DailyQuestion> scenarioDailyQuestions =
        dailyQuestionRepository.findByMemberIdAndDateBetween(scenarioMember.getId(), twoDaysAgo, today);
    assertThat(scenarioDailyQuestions)
        .as("탈퇴 전에는 3일치 일별 질문이 존재해야 함")
        .hasSize(3);
    List<Long> dailyQuestionIds = scenarioDailyQuestions.stream()
        .map(DailyQuestion::getId)
        .toList();
    Set<Long> dailyQuestionIdSet = dailyQuestionIds.stream().collect(Collectors.toSet());
    Set<Long> candidateIds = dailyQuestionCandidateRepository.findAllByDailyQuestionIdInOrderByReceivedOrder(dailyQuestionIds)
        .stream()
        .map(candidate -> candidate.getId())
        .collect(Collectors.toSet());
    assertThat(dailyQuestionAnswerRepository.findAll())
        .as("탈퇴 전에는 답변 데이터가 생성되어 있어야 함")
        .anyMatch(answer -> answer.getId().equals(answerId));

    mockMvc.perform(delete(AUTH_API + "/me")
            .header(HttpHeaders.AUTHORIZATION, scenarioToken))
        .andExpect(status().isNoContent());

    assertThat(memberRepository.findAll())
        .as("질문 API를 여러 번 사용했어도 탈퇴 후 회원 정보가 삭제되어야 함")
        .noneMatch(savedMember -> savedMember.getId().equals(scenarioMember.getId()));
    assertThat(refreshTokenRepository.findAll())
        .as("탈퇴 후 리프레시 토큰이 삭제되어야 함")
        .noneMatch(refreshToken -> refreshToken.getToken().equals("scenario-refresh-token"));
    assertThat(questionCycleRepository.findAll())
        .as("탈퇴 후 질문 사이클이 삭제되어야 함")
        .noneMatch(cycle -> cycle.getId().equals(scenarioCycle.getId()));
    assertThat(dailyQuestionRepository.findAll())
        .as("탈퇴 후 일별 질문이 삭제되어야 함")
        .noneMatch(dailyQuestion -> dailyQuestionIdSet.contains(dailyQuestion.getId()));
    assertThat(dailyQuestionAnswerRepository.findAll())
        .as("탈퇴 후 답변이 삭제되어야 함")
        .noneMatch(answer -> answer.getId().equals(answerId));
    assertThat(dailyQuestionCandidateRepository.findAll())
        .as("탈퇴 후 후보 질문이 삭제되어야 함")
        .noneMatch(candidate -> candidateIds.contains(candidate.getId()));
  }

  @Test
  @DisplayName("공개 질문에 답변/좋아요가 있는 회원도 탈퇴되며, 본인 답변·본인 좋아요·답변에 달린 다른 사람 좋아요까지 모두 삭제된다")
  void withdraw_cleans_up_public_question_answers_and_likes() throws Exception {
    // given - 다른 멤버, PDQ 두 개, 본인 답변, 다른 사람 답변
    Member other = testMemberUtils.createSave();
    Question question1 = testQuestionUtils.createSave();
    Question question2 = testQuestionUtils.createSave();
    LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
    LocalDate yesterday = today.minusDays(1);

    PublicDailyQuestion pdqToday = publicDailyQuestionRepository.save(
        PublicDailyQuestion.publish(question1, today));
    PublicDailyQuestion pdqYesterday = publicDailyQuestionRepository.save(
        PublicDailyQuestion.publish(question2, yesterday));

    // 본인 답변 + 다른 사람 답변
    PublicDailyQuestionAnswer myAnswer = publicDailyQuestionAnswerRepository.save(
        PublicDailyQuestionAnswer.create(pdqToday, member, "내 공개 답변", TIMEZONE));
    PublicDailyQuestionAnswer otherAnswer = publicDailyQuestionAnswerRepository.save(
        PublicDailyQuestionAnswer.create(pdqYesterday, other, "다른 사람 답변", TIMEZONE));

    // (1) 내가 다른 사람 답변에 좋아요
    PublicDailyQuestionAnswerLike myLikeOnOther = publicDailyQuestionAnswerLikeRepository.save(
        PublicDailyQuestionAnswerLike.create(otherAnswer, member));
    // (2) 다른 사람이 내 답변에 좋아요
    PublicDailyQuestionAnswerLike otherLikeOnMine = publicDailyQuestionAnswerLikeRepository.save(
        PublicDailyQuestionAnswerLike.create(myAnswer, other));

    // when - 탈퇴
    mockMvc.perform(delete(AUTH_API + "/me")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNoContent());

    // then - 회원, 본인 답변, 두 좋아요(본인이 누른 것 + 본인 답변에 달린 것) 모두 삭제, 다른 사람 답변은 유지
    assertThat(memberRepository.findById(member.getId()))
        .as("탈퇴 후 회원 정보가 삭제되어야 함")
        .isEmpty();
    assertThat(publicDailyQuestionAnswerRepository.findById(myAnswer.getId()))
        .as("본인이 작성한 공개 답변이 삭제되어야 함")
        .isEmpty();
    assertThat(publicDailyQuestionAnswerRepository.findById(otherAnswer.getId()))
        .as("다른 멤버의 공개 답변은 유지되어야 함")
        .isPresent();
    assertThat(publicDailyQuestionAnswerLikeRepository.findById(myLikeOnOther.getId()))
        .as("본인이 다른 답변에 누른 좋아요가 삭제되어야 함")
        .isEmpty();
    assertThat(publicDailyQuestionAnswerLikeRepository.findById(otherLikeOnMine.getId()))
        .as("본인 답변에 달린 다른 사람 좋아요도 애플리케이션 cleanup 으로 삭제되어야 함")
        .isEmpty();
  }
}
