package site.one_question.api.notification.domain.exception;

public class FcmTokenExpiredException extends RuntimeException {

    public FcmTokenExpiredException(String fcmToken) {
        super("만료된 FCM 토큰: " + fcmToken);
    }
}
