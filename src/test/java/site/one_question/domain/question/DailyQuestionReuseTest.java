package site.one_question.domain.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.test_config.IntegrateTest;

@DisplayName("모든 질문 사용 후 중복 배정 허용 검증")
class DailyQuestionReuseTest extends IntegrateTest {

    @Test
    @DisplayName("전체 질문 소진 뒤에도 동일 질문 재배정 가능")
    // 이 테스트는 사이클 안의 모든 질문이 이미 한 번씩 제공된 상황을 단 하나의 질문으로 단순화한다.
    // 동일 질문을 다시 저장할 수 있어야 daily_question.question_id 유니크 제약이 제거된 것이 검증된다.
    void allow_reassigning_question_after_all_questions_served() {
        // given: 유일한 활성 질문 하나뿐인 상태. 오늘 이미 그 질문을 배정했다고 가정한다.
        Member member = testMemberUtils.createSave();
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        LocalDate today = LocalDate.now();

        testDailyQuestionUtils.createSave_With_Date(member, cycle, question, today);

        // when: 다음 날에 동일한 질문을 다시 배정 (모든 질문 사용 후 중복 허용 여부 확인)
        LocalDate nextDay = today.plusDays(1);
        DailyQuestion reusedDailyQuestion = testDailyQuestionUtils.createSave_With_Date(
                member, cycle, question, nextDay);

        // then: 동일 question_id를 가진 DailyQuestion이 정상적으로 저장되어야 한다.
        assertThat(reusedDailyQuestion.getQuestion().getId())
                .as("재사용된 DailyQuestion의 질문 ID가 전 날의 질문 ID와 일치해야 함 (원본 ID: %d)", question.getId())
                .isEqualTo(question.getId());
        assertThat(reusedDailyQuestion.getQuestionDate())
                .as("재사용된 DailyQuestion의 날짜가 다음 날짜와 일치해야 함 (기대 날짜: %s)", nextDay)
                .isEqualTo(nextDay);
        assertThat(dailyQuestionRepository.findByMemberIdAndDate(member.getId(), nextDay))
                .as("다음 날짜에 해당하는 DailyQuestion이 존재해야 함")
                .map(DailyQuestion::getQuestion)
                .map(Question::getId)
                .as("저장된 질문 ID가 원본 전 날의 ID와 일치해야 함 (원본 ID: %d)", question.getId())
                .hasValue(question.getId());
    }
}
