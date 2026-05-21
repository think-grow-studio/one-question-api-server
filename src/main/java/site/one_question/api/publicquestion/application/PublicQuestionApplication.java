package site.one_question.api.publicquestion.application;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.publicquestion.domain.PublicDailyQuestion;
import site.one_question.api.publicquestion.domain.PublicDailyQuestionService;
import site.one_question.api.publicquestion.presentation.response.GetPublicDailyQuestionResponse;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PublicQuestionApplication {

    private final MemberService memberService;
    private final PublicDailyQuestionService publicDailyQuestionService;

    public GetPublicDailyQuestionResponse getPublicDailyQuestion(Long memberId, LocalDate date) {
        Member member = memberService.findById(memberId);
        PublicDailyQuestion pdq = publicDailyQuestionService.findByDateAndMember(date, member);
        return GetPublicDailyQuestionResponse.from(pdq);
    }
}
