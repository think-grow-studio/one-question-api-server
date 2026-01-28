package site.one_question.member.application;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberService;
import site.one_question.member.presentation.response.GetMemberResponse;
import site.one_question.question.domain.QuestionCycleService;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberApplication {

    private final MemberService memberService;
    private final QuestionCycleService questionCycleService;

    @Transactional(readOnly = true)
    public GetMemberResponse getMe(Long memberId) {
        Member member = memberService.findById(memberId);
        LocalDate firstCycleStartDate = questionCycleService.getFirstCycle(memberId).getStartDate();
        return GetMemberResponse.from(member, firstCycleStartDate);
    }

    public Member updateMe(Long memberId, String fullName, String locale) {
        return memberService.updateMember(memberId, fullName, locale);
    }
}
