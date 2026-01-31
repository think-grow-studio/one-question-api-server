package site.one_question.question.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.exception.QuestionNotFoundException;
import site.one_question.test_config.utils.TestDailyQuestionUtils;
import site.one_question.test_config.utils.TestMemberUtils;
import site.one_question.test_config.utils.TestQuestionCycleUtils;
import site.one_question.test_config.utils.TestQuestionUtils;

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

        Question firstReload = dailyQuestionService.selectRandomQuestionExcluding(cycle, todayDailyQuestion.getQuestion());

        assertThat(firstReload.getId())
                .isNotEqualTo(firstDayId)
                .isNotEqualTo(secondDayId)
                .isNotEqualTo(thirdDayInitialId);

        todayDailyQuestion.changeQuestion(firstReload);
        dailyQuestionRepository.save(todayDailyQuestion);

        Question secondReload = dailyQuestionService.selectRandomQuestionExcluding(cycle, todayDailyQuestion.getQuestion());

        assertThat(secondReload.getId())
                .isNotEqualTo(firstDayId)
                .isNotEqualTo(secondDayId)
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

        assertThatThrownBy(() -> dailyQuestionService.selectRandomQuestionExcluding(cycle, todayDailyQuestion.getQuestion()))
                .isInstanceOf(QuestionNotFoundException.class);
    }
}
