package site.one_question.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.member.domain.Member;
import site.one_question.api.notification.domain.QuestionReminderSetting;
import site.one_question.api.notification.domain.QuestionReminderSettingRepository;

@Component
@RequiredArgsConstructor
public class TestQuestionReminderSettingUtils {

    private final QuestionReminderSettingRepository questionReminderSettingRepository;

    public QuestionReminderSetting createSave(Member member, String alarmTime, String timezone) {
        return questionReminderSettingRepository.save(QuestionReminderSetting.create(member, alarmTime, timezone, true));
    }

    public QuestionReminderSetting createSave_Disabled(Member member, String alarmTime, String timezone) {
        QuestionReminderSetting setting = QuestionReminderSetting.create(member, alarmTime, timezone, false);
        return questionReminderSettingRepository.save(setting);
    }
}
