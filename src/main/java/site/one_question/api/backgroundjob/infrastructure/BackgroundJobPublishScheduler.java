package site.one_question.api.backgroundjob.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.one_question.api.backgroundjob.application.BackgroundJobPublishApplication;

@Slf4j
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
        try {
            publishApplication.publishPendingJobs();
        } catch (Exception e) {
            log.error("BackgroundJob 발행 스케줄러 실행 실패", e);
        }
    }
}
