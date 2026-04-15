package site.one_question.api.notification.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.api.auth.infrastructure.annotation.PrincipalId;
import site.one_question.api.notification.application.FcmTokenApplication;
import site.one_question.api.notification.application.QuestionReminderSettingApplication;
import site.one_question.api.notification.presentation.request.DeleteFcmTokenRequest;
import site.one_question.api.notification.presentation.request.RegisterFcmTokenRequest;
import site.one_question.api.notification.presentation.request.UpsertQuestionReminderSettingRequest;
import site.one_question.api.notification.presentation.response.QuestionReminderSettingResponse;

@Slf4j
@RestController
@RequestMapping("/api/v1/members/me/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final FcmTokenApplication fcmTokenApplication;
    private final QuestionReminderSettingApplication questionReminderSettingApplication;

    @Override
    @PostMapping("/fcm-token")
    public ResponseEntity<Void> registerFcmToken(
            @PrincipalId Long memberId,
            @Valid @RequestBody RegisterFcmTokenRequest request
    ) {
        log.info("[API] FCM 토큰 등록 요청 시작");
        fcmTokenApplication.registerFcmToken(memberId, request);
        log.info("[API] FCM 토큰 등록 요청 종료");
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/fcm-token")
    public ResponseEntity<Void> deleteFcmToken(
            @PrincipalId Long memberId,
            @Valid @RequestBody DeleteFcmTokenRequest request
    ) {
        log.info("[API] FCM 토큰 삭제 요청 시작");
        fcmTokenApplication.deleteFcmToken(memberId, request);
        log.info("[API] FCM 토큰 삭제 요청 종료");
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/settings")
    public ResponseEntity<QuestionReminderSettingResponse> upsertSetting(
            @PrincipalId Long memberId,
            @Valid @RequestBody UpsertQuestionReminderSettingRequest request
    ) {
        log.info("[API] 알림 설정 저장 요청 시작 - alarmTime: {}", request.alarmTime());
        QuestionReminderSettingResponse response = questionReminderSettingApplication.upsert(memberId, request);
        log.info("[API] 알림 설정 저장 요청 종료");
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/settings")
    public ResponseEntity<QuestionReminderSettingResponse> getSetting(
            @PrincipalId Long memberId
    ) {
        log.info("[API] 알림 설정 조회 요청 시작");
        QuestionReminderSettingResponse response = questionReminderSettingApplication.get(memberId);
        log.info("[API] 알림 설정 조회 요청 종료");
        return ResponseEntity.ok(response);
    }
}
