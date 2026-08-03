package site.one_question.integrate.question;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.integrate.test_config.IntegrateTest;

/**
 * DailyQuestion 연관으로 답변을 조회하는 파생 쿼리 검증.
 *
 * <p>이 두 메서드는 엔티티 필드가 {@code dailyQuestionId} → {@code dailyQuestion} 으로
 * rename 될 때 시그니처가 따라가지 않아 오래 깨진 채로 남아 있었다. 파생 쿼리가
 * {@code dailyQuestion.id}(Long) 경로로 해석되는데 파라미터는 엔티티였기 때문이다.
 * 호출자가 없어 테스트가 계속 초록이었으므로, 같은 일이 반복되지 않게 여기서 덮는다.
 */
@DisplayName("DailyQuestion으로 답변 조회 통합 테스트")
class FindAnswerByDailyQuestionIntegrateTest extends IntegrateTest {

    private Member member;
    private DailyQuestion dailyQuestion;

    @BeforeEach
    void setUp() {
        member = testMemberUtils.createSave();
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave_With_Content("연관 조회 질문");
        dailyQuestion = testDailyQuestionUtils.createSave(member, cycle, question);
    }

    @Test
    @DisplayName("답변이 있으면 존재 여부와 본문을 조회한다")
    void find_by_daily_question_when_answered_then_returns_answer() {
        DailyQuestionAnswer saved = testDailyQuestionAnswerUtils.createSave_With_Content(
                dailyQuestion, member, "연관 조회 답변");

        assertThat(dailyQuestionAnswerRepository.existsByDailyQuestion(dailyQuestion))
                .as("답변이 있으면 true여야 함")
                .isTrue();
        assertThat(dailyQuestionAnswerRepository.findByDailyQuestion(dailyQuestion))
                .as("해당 DailyQuestion의 답변을 찾아야 함")
                .get()
                .extracting(DailyQuestionAnswer::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("답변이 없으면 존재하지 않고 빈 값을 반환한다")
    void find_by_daily_question_when_not_answered_then_returns_empty() {
        assertThat(dailyQuestionAnswerRepository.existsByDailyQuestion(dailyQuestion))
                .as("답변이 없으면 false여야 함")
                .isFalse();
        assertThat(dailyQuestionAnswerRepository.findByDailyQuestion(dailyQuestion))
                .as("답변이 없으면 빈 Optional이어야 함")
                .isEmpty();
    }
}
