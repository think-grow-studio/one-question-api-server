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
                // [알림 페이로드] OS(APNs/FCM)가 자동으로 알림 배너 표시
                // 앱이 백그라운드/종료 상태일 때 시스템이 처리
                // iOS 포그라운드에서는 자동 표시 안 됨 → 아래 데이터 페이로드로 처리
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                // [데이터 페이로드] 앱 코드의 onMessage 핸들러로 전달되는 key-value
                // iOS 포그라운드 상태에서 앱이 title/body를 읽어 직접 알림을 띄울 수 있도록 함
            .putData("title",title)
            .putData("body",body)
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
