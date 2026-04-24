package site.one_question.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.notification.domain.QuestionReminderSetting;
import site.one_question.api.notification.domain.QuestionReminderSettingService;
import site.one_question.api.notification.presentation.request.SetQuestionReminderSettingRequest;
import site.one_question.api.notification.presentation.response.SetQuestionReminderSettingResponse;

@Service
@Transactional
@RequiredArgsConstructor
public class QuestionReminderSettingApplication {

    private final MemberService memberService;
    private final QuestionReminderSettingService questionReminderSettingService;

    public SetQuestionReminderSettingResponse set(Long memberId, SetQuestionReminderSettingRequest request) {
        Member member = memberService.findById(memberId);
        QuestionReminderSetting setting = questionReminderSettingService.upsert(
                member,
                request.alarmTime(),
                request.timezone(),
                request.enabled()
        );
        return SetQuestionReminderSettingResponse.from(setting);
    }

    @Transactional(readOnly = true)
    public SetQuestionReminderSettingResponse get(Long memberId) {
        Member member = memberService.findById(memberId);
        return SetQuestionReminderSettingResponse.from(questionReminderSettingService.findByMemberOrThrow(member));
    }
}
