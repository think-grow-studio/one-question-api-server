package site.one_question.question.application;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberRepository;
import site.one_question.member.domain.MemberService;
import site.one_question.member.domain.exception.MemberNotFoundException;
import site.one_question.question.domain.DailyQuestion;
import site.one_question.question.domain.DailyQuestionService;
import site.one_question.question.domain.Question;
import site.one_question.question.domain.QuestionCycle;
import site.one_question.question.domain.QuestionCycleService;
import site.one_question.question.presentation.response.ServeDailyQuestionResponse;

@Service
@Transactional
@RequiredArgsConstructor
public class QuestionApplication {

    private final DailyQuestionService dailyQuestionService;
    private final QuestionCycleService cycleService;
    private final MemberService memberService;

    public ServeDailyQuestionResponse serveDailyQuestion(Long memberId, LocalDate date, String timezone) {
        // 멱등성: 기존 질문 있으면 반환
        Optional<DailyQuestion> existing = dailyQuestionService.findByMemberIdAndDate(memberId, date);
        if (existing.isPresent()) {
            DailyQuestion dailyQuestion = existing.get();
            return ServeDailyQuestionResponse.from(dailyQuestion, dailyQuestion.getQuestion(), dailyQuestion.getQuestionCycle());
        }

        Member member = memberService.findById(memberId);
        QuestionCycle cycle = cycleService.getOrCreateCycle(member, date, timezone);
        Question selectedQuestion = dailyQuestionService.selectRandomQuestion(cycle.getId());

        // 5. DailyQuestion 생성 및 저장
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member,
                cycle,
                selectedQuestion,
                date,
                timezone
        );
        DailyQuestion saved = dailyQuestionService.save(dailyQuestion);

        return ServeDailyQuestionResponse.from(saved, selectedQuestion, cycle);
    }
}
