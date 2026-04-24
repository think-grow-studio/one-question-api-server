package site.one_question.api.notification.domain.exception;

import java.util.Map;

public class QuestionReminderSettingNotFoundException extends NotificationException {

    public QuestionReminderSettingNotFoundException(Long memberId) {
        super(NotificationExceptionSpec.NOTIFICATION_SETTING_NOT_FOUND, Map.of("memberId", memberId));
    }
}
