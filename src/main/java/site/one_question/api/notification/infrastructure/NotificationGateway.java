package site.one_question.api.notification.infrastructure;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.one_question.api.notification.domain.exception.FcmTokenExpiredException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationGateway {

    private final FirebaseMessaging firebaseMessaging;

    public void sendNotification(Long memberId, String fcmToken, String title, String body) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
        try {
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                throw new FcmTokenExpiredException(fcmToken);
            }
            log.error("FCM 전송 실패: memberId={}, errorCode={}, msg={}",
                    memberId, code, e.getMessage(), e);
        }
    }
}
