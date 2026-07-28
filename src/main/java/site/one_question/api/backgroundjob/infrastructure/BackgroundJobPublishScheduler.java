package site.one_question.api.backgroundjob.infrastructure;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.one_question.api.backgroundjob.application.BackgroundJobPublishApplication;

@Component
@ConditionalOnProperty(
        name = "app.background-job.publisher.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class BackgroundJobPublishScheduler {

    private final BackgroundJobPublishApplication publishApplication;

    @Scheduled(
            fixedDelayString = "${app.background-job.publisher.fixed-delay-ms:5000}"
    )
    @SchedulerLock(name = "backgroundJobPublishScheduler", lockAtMostFor = "PT1M")
    public void publish() {
        publishApplication.publishPendingJobs();
    }
}
