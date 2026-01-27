package site.one_question.test_config.utils;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.DailyQuestionRepository;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionCycle;

@Component
@RequiredArgsConstructor
public class TestDailyQuestionUtils {

    private final DailyQuestionRepository dailyQuestionRepository;

    public DailyQuestion createSave(Member member, QuestionCycle cycle, Question question) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                question,
                LocalDate.now(),
                "Asia/Seoul"
        );
        return dailyQuestionRepository.save(dailyQuestion);
    }

    public DailyQuestion createSave_With_Date(Member member, QuestionCycle cycle, Question question, LocalDate date) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                question,
                date,
                "Asia/Seoul"
        );
        return dailyQuestionRepository.save(dailyQuestion);
    }

    public DailyQuestion createSave_With_Timezone(Member member, QuestionCycle cycle, Question question, String timezone) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                question,
                LocalDate.now(),
                timezone
        );
        return dailyQuestionRepository.save(dailyQuestion);
    }
}
