package site.one_question.integrate.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import site.one_question.api.member.domain.Member;
import site.one_question.api.member.domain.MemberStatus;
import site.one_question.api.notification.application.NotificationScheduler;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("알림 스케줄러 통합 테스트")
class NotificationSchedulerIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private NotificationScheduler notificationScheduler;

    @Autowired
    private FirebaseMessaging firebaseMessaging;

    private Member member;
    private String currentAlarmTime;

    @BeforeEach
    void setUp() {
        Mockito.reset(firebaseMessaging);
        member = testMemberUtils.createSave_With_Locale("en-US");
        currentAlarmTime = LocalTime.now(ZoneId.of(TIMEZONE)).format(TIME_FORMATTER);
    }

    @Test
    @DisplayName("알람 시각이 일치하는 활성 토큰에 FCM 알림을 전송한다")
    void sendAlarmNotifications_sendsNotification_whenAlarmTimeMatches() throws Exception {
        testFcmTokenUtils.createSave(member, "test-token");
        testQuestionReminderSettingUtils.createSave(member, currentAlarmTime, TIMEZONE);

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    @DisplayName("만료된 FCM 토큰으로 전송 실패 시 해당 토큰을 DB에서 삭제한다")
    void sendAlarmNotifications_deletesToken_whenTokenIsExpired() throws Exception {
        testFcmTokenUtils.createSave(member, "expired-token");
        testQuestionReminderSettingUtils.createSave(member, currentAlarmTime, TIMEZONE);

        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(ex);

        notificationScheduler.sendAlarmNotifications();

        assertThat(fcmTokenRepository.findByMember(member)).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 타임존 멤버에게 각자의 현지 시각에 맞게 알림을 전송한다")
    void sendAlarmNotifications_sendsNotification_respectingEachMembersTimezone() throws Exception {
        String seoulTime = LocalTime.now(ZoneId.of("Asia/Seoul")).format(TIME_FORMATTER);
        String newYorkTime = LocalTime.now(ZoneId.of("America/New_York")).format(TIME_FORMATTER);

        Member seoulMember = testMemberUtils.createSave();
        Member newYorkMember = testMemberUtils.createSave();
        testFcmTokenUtils.createSave(seoulMember, "seoul-token");
        testFcmTokenUtils.createSave(newYorkMember, "new-york-token");
        testQuestionReminderSettingUtils.createSave(seoulMember, seoulTime, "Asia/Seoul");
        testQuestionReminderSettingUtils.createSave(newYorkMember, newYorkTime, "America/New_York");

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, times(2)).send(any(Message.class));
    }

    @Test
    @DisplayName("같은 alarmTime 문자열이라도 타임존이 다르면 현지 시각이 불일치한 멤버에게는 알림을 전송하지 않는다")
    void sendAlarmNotifications_sendsOnlyToMemberWhoseLocalTimeMatches_whenSameAlarmTimeStringButDifferentTimezone() throws Exception {
        String seoulCurrentTime = LocalTime.now(ZoneId.of("Asia/Seoul")).format(TIME_FORMATTER);

        Member seoulMember = testMemberUtils.createSave();
        Member newYorkMember = testMemberUtils.createSave();
        testFcmTokenUtils.createSave(seoulMember, "seoul-token");
        testFcmTokenUtils.createSave(newYorkMember, "new-york-token");
        testQuestionReminderSettingUtils.createSave(seoulMember, seoulCurrentTime, "Asia/Seoul");
        testQuestionReminderSettingUtils.createSave(newYorkMember, seoulCurrentTime, "America/New_York");

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    @DisplayName("알람이 비활성화된 멤버에게는 FCM 알림을 전송하지 않는다")
    void sendAlarmNotifications_skipsNotification_whenAlarmIsDisabled() throws Exception {
        testFcmTokenUtils.createSave(member, "test-token");
        testQuestionReminderSettingUtils.createSave_Disabled(member, currentAlarmTime, TIMEZONE);

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    @Test
    @DisplayName("알람 시각이 불일치하는 멤버에게는 FCM 알림을 전송하지 않는다")
    void sendAlarmNotifications_skipsNotification_whenAlarmTimeDoesNotMatch() throws Exception {
        testFcmTokenUtils.createSave(member, "test-token");
        String nextMinute = LocalTime.now(ZoneId.of(TIMEZONE)).plusMinutes(1).format(TIME_FORMATTER);
        testQuestionReminderSettingUtils.createSave(member, nextMinute, TIMEZONE);

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    @Test
    @DisplayName("알람 시각이 일치하는 여러 멤버에게 모두 FCM 알림을 전송한다")
    void sendAlarmNotifications_sendsNotification_toAllMatchingMembers() throws Exception {
        Member member2 = testMemberUtils.createSave();
        Member member3 = testMemberUtils.createSave();
        testFcmTokenUtils.createSave(member, "token-1");
        testFcmTokenUtils.createSave(member2, "token-2");
        testFcmTokenUtils.createSave(member3, "token-3");
        testQuestionReminderSettingUtils.createSave(member, currentAlarmTime, TIMEZONE);
        testQuestionReminderSettingUtils.createSave(member2, currentAlarmTime, TIMEZONE);
        testQuestionReminderSettingUtils.createSave(member3, currentAlarmTime, TIMEZONE);

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, times(3)).send(any(Message.class));
    }

    @Test
    @DisplayName("만료되지 않은 FCM 에러 발생 시 토큰을 삭제하지 않는다")
    void sendAlarmNotifications_keepsToken_whenNonExpiredFcmErrorOccurs() throws Exception {
        testFcmTokenUtils.createSave(member, "test-token");
        testQuestionReminderSettingUtils.createSave(member, currentAlarmTime, TIMEZONE);

        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(ex);

        notificationScheduler.sendAlarmNotifications();

        assertThat(fcmTokenRepository.findByMember(member)).isPresent();
    }

    @Test
    @DisplayName("한 명의 토큰이 만료되어도 다른 멤버에게는 알림을 전송한다")
    void sendAlarmNotifications_continuesForOtherMembers_whenOneTokenExpires() throws Exception {
        Member member2 = testMemberUtils.createSave();
        testFcmTokenUtils.createSave(member, "expired-token");
        testFcmTokenUtils.createSave(member2, "valid-token");
        testQuestionReminderSettingUtils.createSave(member, currentAlarmTime, TIMEZONE);
        testQuestionReminderSettingUtils.createSave(member2, currentAlarmTime, TIMEZONE);

        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(ex)
                .thenReturn("message-id");

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, times(2)).send(any(Message.class));
        assertThat(fcmTokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("ACTIVE가 아닌 멤버에게는 FCM 알림을 전송하지 않는다")
    void sendAlarmNotifications_skipsNotification_whenMemberIsNotActive() throws Exception {
        testFcmTokenUtils.createSave(member, "test-token");
        testQuestionReminderSettingUtils.createSave(member, currentAlarmTime, TIMEZONE);

        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("UPDATE Member m SET m.status = :status WHERE m.id = :id")
                        .setParameter("status", MemberStatus.WITHDRAWAL_REQUESTED)
                        .setParameter("id", member.getId())
                        .executeUpdate()
        );

        notificationScheduler.sendAlarmNotifications();

        verify(firebaseMessaging, never()).send(any(Message.class));
    }
}
