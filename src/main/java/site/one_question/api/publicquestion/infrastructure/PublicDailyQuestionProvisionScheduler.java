package site.one_question.api.publicquestion.infrastructure;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.one_question.api.publicquestion.application.PublicDailyQuestionProvisionApplication;

@Component
@RequiredArgsConstructor
public class PublicDailyQuestionProvisionScheduler {

    private final PublicDailyQuestionProvisionApplication provisioner;

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    @SchedulerLock(
            name = "publicDailyQuestionProvisionScheduler",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT10M"
    )
    public void provisionPublicDailyQuestions() {
        provisioner.provision();
    }
}
