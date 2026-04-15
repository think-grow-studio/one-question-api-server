package site.one_question.api.notification.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.global.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum NotificationExceptionSpec implements ExceptionSpec {
    NOTIFICATION_SETTING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "NOTIFICATION-001",
            "알림 설정을 찾을 수 없음",
            "error.notification.setting.not-found"
    ),
    FCM_TOKEN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "NOTIFICATION-002",
            "FCM 토큰을 찾을 수 없음",
            "error.notification.fcm-token.not-found"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
