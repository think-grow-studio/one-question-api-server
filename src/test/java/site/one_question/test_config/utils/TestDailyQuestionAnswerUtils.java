package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.DailyQuestionAnswer;
import site.one_question.question.domain.DailyQuestionAnswerRepository;

@Component
@RequiredArgsConstructor
public class TestDailyQuestionAnswerUtils {

    private final DailyQuestionAnswerRepository repository;
    private static int uniqueId = 0;

    public DailyQuestionAnswer createSave(DailyQuestion dailyQuestion, Member member) {
        DailyQuestionAnswer answer = DailyQuestionAnswer.create(
                dailyQuestion,
                member,
                "테스트 답변 내용 " + uniqueId++,
                "Asia/Seoul"
        );
        return repository.save(answer);
    }

    public DailyQuestionAnswer createSave_With_Content(DailyQuestion dailyQuestion, Member member, String content) {
        DailyQuestionAnswer answer = DailyQuestionAnswer.create(
                dailyQuestion,
                member,
                content,
                "Asia/Seoul"
        );
        return repository.save(answer);
    }

    public DailyQuestionAnswer createSave_With_Timezone(DailyQuestion dailyQuestion, Member member, String timezone) {
        DailyQuestionAnswer answer = DailyQuestionAnswer.create(
                dailyQuestion,
                member,
                "테스트 답변 내용 " + uniqueId++,
                timezone
        );
        return repository.save(answer);
    }
}
