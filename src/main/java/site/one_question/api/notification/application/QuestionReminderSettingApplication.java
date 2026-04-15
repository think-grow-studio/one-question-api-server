package site.one_question.api.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberService;
import site.one_question.api.notification.domain.QuestionReminderSetting;
import site.one_question.api.notification.domain.QuestionReminderSettingService;
import site.one_question.api.notification.presentation.request.UpsertQuestionReminderSettingRequest;
import site.one_question.api.notification.presentation.response.QuestionReminderSettingResponse;

@Service
@Transactional
@RequiredArgsConstructor
public class QuestionReminderSettingApplication {

    private final MemberService memberService;
    private final QuestionReminderSettingService questionReminderSettingService;

    public QuestionReminderSettingResponse upsert(Long memberId, UpsertQuestionReminderSettingRequest request) {
        Member member = memberService.findById(memberId);
        QuestionReminderSetting setting = questionReminderSettingService.upsert(
                member,
                request.alarmTime(),
                request.timezone(),
                request.enabled()
        );
        return QuestionReminderSettingResponse.from(setting);
    }

    @Transactional(readOnly = true)
    public QuestionReminderSettingResponse get(Long memberId) {
        Member member = memberService.findById(memberId);
        return QuestionReminderSettingResponse.from(questionReminderSettingService.findByMemberOrThrow(member));
    }
}
