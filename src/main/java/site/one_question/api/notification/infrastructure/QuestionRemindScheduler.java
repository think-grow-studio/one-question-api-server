package site.one_question.api.notification.infrastructure;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.one_question.api.notification.application.QuestionRemindApplication;

@Component
@RequiredArgsConstructor
public class QuestionRemindScheduler {

    private final QuestionRemindApplication questionRemindApplication;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "questionRemindScheduler", lockAtLeastFor = "PT55S", lockAtMostFor = "PT55S")
    public void sendAlarmNotifications() {
        questionRemindApplication.sendAlarmNotifications();
    }
}
