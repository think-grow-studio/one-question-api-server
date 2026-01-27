package site.one_question.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.member.domain.Member;
import site.one_question.member.domain.MemberService;
import site.one_question.member.presentation.response.GetMemberResponse;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberApplication {

    private final MemberService memberService;

    @Transactional(readOnly = true)
    public GetMemberResponse getMe(Long memberId) {
        Member member = memberService.findById(memberId);
        return GetMemberResponse.from(member);
    }

    public Member updateMe(Long memberId, String fullName, String locale) {
        return memberService.updateMember(memberId, fullName, locale);
    }
}
