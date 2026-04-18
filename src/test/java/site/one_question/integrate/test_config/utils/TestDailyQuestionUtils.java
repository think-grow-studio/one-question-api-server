package site.one_question.integrate.test_config.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionCandidate;
import site.one_question.api.question.domain.DailyQuestionCandidateRepository;
import site.one_question.api.question.domain.DailyQuestionRepository;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;

@Component
@RequiredArgsConstructor
public class TestDailyQuestionUtils {

    private final DailyQuestionRepository dailyQuestionRepository;
    private final DailyQuestionCandidateRepository candidateRepository;

    public DailyQuestion createSave(Member member, QuestionCycle cycle, Question question) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                question,
                LocalDate.now(ZoneId.of("Asia/Seoul")),
                "Asia/Seoul"
        );
        DailyQuestion saved = dailyQuestionRepository.save(dailyQuestion);
        candidateRepository.save(DailyQuestionCandidate.create(saved, question, 1));
        return saved;
    }

    public DailyQuestion createSave_With_Date(Member member, QuestionCycle cycle, Question question, LocalDate date) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                question,
                date,
                "Asia/Seoul"
        );
        DailyQuestion saved = dailyQuestionRepository.save(dailyQuestion);
        candidateRepository.save(DailyQuestionCandidate.create(saved, question, 1));
        return saved;
    }

    public DailyQuestion createSave_With_Timezone(Member member, QuestionCycle cycle, Question question, String timezone) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                question,
                LocalDate.now(ZoneId.of("Asia/Seoul")),
                timezone
        );
        DailyQuestion saved = dailyQuestionRepository.save(dailyQuestion);
        candidateRepository.save(DailyQuestionCandidate.create(saved, question, 1));
        return saved;
    }
}
