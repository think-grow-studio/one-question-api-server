package site.one_question.api.notification.domain.exception;

import java.util.Map;

public class FcmTokenNotFoundException extends NotificationException {

    public FcmTokenNotFoundException(Long memberId) {
        super(NotificationExceptionSpec.FCM_TOKEN_NOT_FOUND, Map.of("memberId", memberId));
    }
}
