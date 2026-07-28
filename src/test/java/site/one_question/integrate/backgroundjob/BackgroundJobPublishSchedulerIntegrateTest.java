package site.one_question.integrate.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.inmemory.InMemoryLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import site.one_question.api.backgroundjob.application.BackgroundJobPublishApplication;
import site.one_question.api.backgroundjob.infrastructure.BackgroundJobPublishScheduler;

class BackgroundJobPublishSchedulerIntegrateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    BackgroundJobPublishScheduler.class,
                    SchedulerTestConfiguration.class);

    @Test
    void scheduler_is_absent_when_disabled() {
        contextRunner
                .withPropertyValues("app.background-job.publisher.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(BackgroundJobPublishScheduler.class));
    }

    @Test
    void scheduler_uses_configurable_five_second_delay_and_stable_lock_name()
            throws NoSuchMethodException {
        contextRunner
                .withPropertyValues("app.background-job.publisher.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(BackgroundJobPublishScheduler.class));

        Method publish = BackgroundJobPublishScheduler.class.getDeclaredMethod("publish");
        Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(
                publish, Scheduled.class);
        SchedulerLock schedulerLock = AnnotatedElementUtils.findMergedAnnotation(
                publish, SchedulerLock.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${app.background-job.publisher.fixed-delay-ms:5000}");
        assertThat(schedulerLock).isNotNull();
        assertThat(schedulerLock.name()).isEqualTo("backgroundJobPublishScheduler");
        assertThat(schedulerLock.lockAtMostFor()).isEqualTo("PT1M");
    }

    @Test
    void concurrent_proxied_invocations_execute_application_once() {
        contextRunner
                .withPropertyValues("app.background-job.publisher.enabled=true")
                .run(context -> {
                    BackgroundJobPublishScheduler scheduler =
                            context.getBean(BackgroundJobPublishScheduler.class);
                    BackgroundJobPublishApplication application =
                            context.getBean(BackgroundJobPublishApplication.class);
                    CountDownLatch applicationEntered = new CountDownLatch(1);
                    CountDownLatch releaseApplication = new CountDownLatch(1);
                    AtomicInteger applicationExecutions = new AtomicInteger();

                    doAnswer(invocation -> {
                        applicationExecutions.incrementAndGet();
                        applicationEntered.countDown();
                        await(releaseApplication, "첫 번째 application 실행 해제");
                        return null;
                    }).when(application).publishPendingJobs();

                    assertThat(AopUtils.isAopProxy(scheduler))
                            .as("@SchedulerLock 메서드는 Spring AOP proxy를 통해 호출돼야 한다")
                            .isTrue();
                    assertThat(context).hasSingleBean(LockProvider.class);

                    ExecutorService executor = Executors.newFixedThreadPool(2);
                    try {
                        Future<?> first = executor.submit(scheduler::publish);
                        await(applicationEntered, "첫 번째 application 실행 진입");

                        Future<?> second = executor.submit(scheduler::publish);
                        await(second, "잠금에 실패한 두 번째 scheduler 호출 종료");

                        releaseApplication.countDown();
                        await(first, "첫 번째 scheduler 호출 종료");

                        assertThat(applicationExecutions.get())
                                .as("동일한 ShedLock 이름의 동시 호출 중 하나만 application을 실행해야 한다")
                                .isEqualTo(1);
                    } finally {
                        releaseApplication.countDown();
                        executor.shutdownNow();
                        awaitTermination(executor);
                    }
                });
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(description + " 대기 시간이 초과됐다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(description + " 대기 중 스레드가 중단됐다", exception);
        }
    }

    private static void await(Future<?> future, String description) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(description + " 대기 중 실패했다", exception);
        }
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("scheduler 경쟁 테스트 executor 종료 시간이 초과됐다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("scheduler 경쟁 테스트 executor 종료 중 스레드가 중단됐다", exception);
        }
    }

    @TestConfiguration
    @EnableSchedulerLock(
            defaultLockAtMostFor = "PT1M",
            interceptMode = EnableSchedulerLock.InterceptMode.PROXY_METHOD
    )
    static class SchedulerTestConfiguration {

        @Bean
        BackgroundJobPublishApplication backgroundJobPublishApplication() {
            return mock(BackgroundJobPublishApplication.class);
        }

        @Bean
        LockProvider lockProvider() {
            return new InMemoryLockProvider();
        }
    }
}
