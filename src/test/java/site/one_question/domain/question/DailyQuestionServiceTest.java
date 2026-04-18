package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionRepository;
import site.one_question.api.question.domain.DailyQuestionService;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.api.question.domain.exception.QuestionNotFoundException;
import site.one_question.integrate.test_config.utils.TestDailyQuestionUtils;
import site.one_question.integrate.test_config.utils.TestMemberUtils;
import site.one_question.integrate.test_config.utils.TestQuestionCycleUtils;
import site.one_question.integrate.test_config.utils.TestQuestionUtils;

@DataJpaTest
@Import({
        DailyQuestionService.class,
        TestMemberUtils.class,
        TestQuestionCycleUtils.class,
        TestQuestionUtils.class,
        TestDailyQuestionUtils.class
})
class DailyQuestionServiceTest {

    private static final String TIMEZONE = "Asia/Seoul";

    @Autowired
    private DailyQuestionService dailyQuestionService;

    @Autowired
    private DailyQuestionRepository dailyQuestionRepository;

    @Autowired
    private TestMemberUtils testMemberUtils;

    @Autowired
    private TestQuestionCycleUtils testQuestionCycleUtils;

    @Autowired
    private TestQuestionUtils testQuestionUtils;

    @Autowired
    private TestDailyQuestionUtils testDailyQuestionUtils;

    @Test
    @DisplayName("3일차 연속 새로고침 시 과거 및 직전 질문과 중복되지 않는다")
    void selectRandomQuestionExcluding_returns_unique_questions_across_days() {
        Member member = testMemberUtils.createSave();
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);

        Question firstDayQuestion = testQuestionUtils.createSave();
        Question secondDayQuestion = testQuestionUtils.createSave();
        Question thirdDayQuestion = testQuestionUtils.createSave();
        testQuestionUtils.createSave(); // reload candidate 1
        testQuestionUtils.createSave(); // reload candidate 2

        LocalDate today = LocalDate.now();
        testDailyQuestionUtils.createSave_With_Date(member, cycle, firstDayQuestion, today.minusDays(2));
        testDailyQuestionUtils.createSave_With_Date(member, cycle, secondDayQuestion, today.minusDays(1));
        DailyQuestion todayDailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, thirdDayQuestion, today);

        Long firstDayId = firstDayQuestion.getId();
        Long secondDayId = secondDayQuestion.getId();
        Long thirdDayInitialId = thirdDayQuestion.getId();

        List<Long> firstCandidateIds = List.of(thirdDayQuestion.getId());
        Question firstReload = dailyQuestionService.selectRandomQuestionExcluding(cycle, firstCandidateIds);

        assertThat(firstReload.getId())
                .as("첫 번째 새로고침 후 질문이 첫째 날 질문과 달라야 함 (첫째 날 ID: %d)", firstDayId)
                .isNotEqualTo(firstDayId)
                .as("첫 번째 새로고침 후 질문이 둘째 날 질문과 달라야 함 (둘째 날 ID: %d)", secondDayId)
                .isNotEqualTo(secondDayId)
                .as("첫 번째 새로고침 후 질문이 셋째 날 초기 질문과 달라야 함 (초기 ID: %d)", thirdDayInitialId)
                .isNotEqualTo(thirdDayInitialId);

        todayDailyQuestion.changeQuestion(firstReload);
        dailyQuestionRepository.save(todayDailyQuestion);

        List<Long> secondCandidateIds = List.of(thirdDayQuestion.getId(), firstReload.getId());
        Question secondReload = dailyQuestionService.selectRandomQuestionExcluding(cycle, secondCandidateIds);

        assertThat(secondReload.getId())
                .as("두 번째 새로고침 후 질문이 첫째 날 질문과 달라야 함 (첫째 날 ID: %d)", firstDayId)
                .isNotEqualTo(firstDayId)
                .as("두 번째 새로고침 후 질문이 둘째 날 질문과 달라야 함 (둘째 날 ID: %d)", secondDayId)
                .isNotEqualTo(secondDayId)
                .as("두 번째 새로고침 후 질문이 첫 번째 새로고침 질문과 달라야 함 (첫 번째 새로고침 ID: %d)", firstReload.getId())
                .isNotEqualTo(firstReload.getId());
    }

    @Test
    @DisplayName("후보 질문이 없으면 QuestionNotFoundException")
    void selectRandomQuestionExcluding_throws_when_no_candidate() {
        Member member = testMemberUtils.createSave();
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);

        Question onlyQuestion = testQuestionUtils.createSave();
        LocalDate today = LocalDate.now();
        testDailyQuestionUtils.createSave_With_Date(member, cycle, onlyQuestion, today.minusDays(1));
        DailyQuestion todayDailyQuestion = testDailyQuestionUtils.createSave_With_Date(member, cycle, onlyQuestion, today);

        List<Long> candidateIds = List.of(onlyQuestion.getId());
        assertThatThrownBy(() -> dailyQuestionService.selectRandomQuestionExcluding(cycle, candidateIds))
                .as("후보 질문이 없을 때 QuestionNotFoundException이 발생해야 함")
                .isInstanceOf(QuestionNotFoundException.class);
    }
}
