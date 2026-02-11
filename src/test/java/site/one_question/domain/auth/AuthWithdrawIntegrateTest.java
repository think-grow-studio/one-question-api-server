package site.one_question.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.test_config.IntegrateTest;

@DisplayName("회원 탈퇴 API 통합 테스트")
class AuthWithdrawIntegrateTest extends IntegrateTest {

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

    System.out.println("=== API 호출 완료 ===");

    assertThat(memberRepository.findById(member.getId()))
        .as("탈퇴 후 회원 정보가 삭제되어야 함")
        .isEmpty();

    var tokenAfterDelete = refreshTokenRepository.findByMember_Id(member.getId());
    System.out.println("삭제 후 RefreshToken 존재: " + tokenAfterDelete.isPresent());

    assertThat(tokenAfterDelete)
        .as("탈퇴 후 리프레시 토큰이 삭제되어야 함")
        .isEmpty();
  }
}
