package site.one_question.api.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.notification.domain.FcmToken;
import site.one_question.api.notification.domain.FcmTokenService;
import site.one_question.api.notification.domain.QuestionReminderSettingService;
import site.one_question.api.notification.infrastructure.NotificationGateway;
import site.one_question.api.notification.domain.exception.FcmTokenExpiredException;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final String NOTIFICATION_TITLE_KEY = "notification.question-reminder.title";
    private static final String NOTIFICATION_BODY_KEY = "notification.question-reminder.body";
    private static final Locale DEFAULT_LOCALE = Locale.KOREAN;

    private final QuestionReminderSettingService questionReminderSettingService;
    private final FcmTokenService fcmTokenService;
    private final NotificationGateway notificationGateway;
    private final MessageSource messageSource;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "sendAlarmNotifications", lockAtLeastFor = "PT55S", lockAtMostFor = "PT55S")
    public void sendAlarmNotifications() {
        List<String> timezones = questionReminderSettingService.findDistinctActiveTimezones();
        if (timezones.isEmpty()) {
            return;
        }

        for (String timezone : timezones) {
            List<FcmToken> fcmTokens = fcmTokenService.findTokensForNotification(Instant.now(), timezone);

            for (FcmToken fcmToken : fcmTokens) {
                try {
                    Locale locale = getMemberLocale(fcmToken.getMember().getLocale());
                    String title = messageSource.getMessage(
                            NOTIFICATION_TITLE_KEY,
                            null,
                            NOTIFICATION_TITLE_KEY,
                            locale
                    );
                    String body = messageSource.getMessage(
                            NOTIFICATION_BODY_KEY,
                            null,
                            NOTIFICATION_BODY_KEY,
                            locale
                    );
                    notificationGateway.sendNotification(fcmToken.getMember().getId(), fcmToken.getToken(), title, body);
                    log.debug("FCM 알림 전송 완료: memberId={}", fcmToken.getMember().getId());
                } catch (FcmTokenExpiredException e) {
                    transactionTemplate.executeWithoutResult(status ->
                            fcmTokenService.delete(fcmToken.getMember(), fcmToken.getToken()));
                    log.warn("FCM 토큰 만료, 삭제 처리: memberId={}", fcmToken.getMember().getId());
                } catch (Exception e) {
                    log.error("FCM 알림 전송 실패: memberId={}", fcmToken.getMember().getId(), e);
                }
            }
        }
    }

    private Locale getMemberLocale(String localeValue) {
        if (localeValue == null || localeValue.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return Locale.forLanguageTag(localeValue);
    }
}
