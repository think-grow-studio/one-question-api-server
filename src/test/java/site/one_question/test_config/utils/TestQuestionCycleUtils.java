package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.member.domain.Member;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.question.domain.QuestionCycleRepository;

@Component
@RequiredArgsConstructor
public class TestQuestionCycleUtils {

    private final QuestionCycleRepository questionCycleRepository;

    public QuestionCycle createSave(Member member) {
        QuestionCycle cycle = QuestionCycle.createFirstCycle(member, "Asia/Seoul");
        return questionCycleRepository.save(cycle);
    }

    public QuestionCycle createSave_With_Timezone(Member member, String timezone) {
        QuestionCycle cycle = QuestionCycle.createFirstCycle(member, timezone);
        return questionCycleRepository.save(cycle);
    }
}
