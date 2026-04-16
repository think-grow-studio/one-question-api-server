package site.one_question.api.member.application;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.member.presentation.response.GetMemberResponse;
import site.one_question.api.notification.domain.QuestionReminderSettingService;
import site.one_question.api.notification.presentation.response.QuestionReminderSettingResponse;
import site.one_question.api.question.domain.QuestionCycleService;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberApplication {

    private final MemberService memberService;
    private final QuestionCycleService questionCycleService;
    private final QuestionReminderSettingService questionReminderSettingService;

    @Transactional(readOnly = true)
    public GetMemberResponse getMe(Long memberId) {
        Member member = memberService.findById(memberId);
        LocalDate firstCycleStartDate = questionCycleService.getFirstCycle(memberId).getStartDate();
        QuestionReminderSettingResponse notificationSetting = questionReminderSettingService
                .findByMember(member)
                .map(QuestionReminderSettingResponse::from)
                .orElse(null);
        return GetMemberResponse.from(member, firstCycleStartDate, notificationSetting);
    }

    public Member updateMe(Long memberId, String fullName, String locale) {
        return memberService.updateMember(memberId, fullName, locale);
    }
}
