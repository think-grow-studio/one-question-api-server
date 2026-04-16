package site.one_question.api.notification.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.one_question.api.notification.presentation.request.DeleteFcmTokenRequest;
import site.one_question.api.notification.presentation.request.RegisterFcmTokenRequest;
import site.one_question.api.notification.presentation.request.SetQuestionReminderSettingRequest;
import site.one_question.api.notification.presentation.response.SetQuestionReminderSettingResponse;

@Tag(name = "Notification", description = "알림 관련 API")
public interface NotificationApi {

    @Operation(
            summary = "FCM 토큰 등록",
            description = "디바이스의 FCM 토큰을 등록합니다. 이미 등록된 토큰이 있으면 새 토큰으로 교체됩니다. (Upsert)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "인증 정보 없음",
                                            value = """
                                                    {
                                                        "code": "AUTH-007",
                                                        "message": "로그인이 필요합니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 무효",
                                            value = """
                                                    {
                                                        "code": "AUTH-009",
                                                        "message": "유효하지 않은 토큰입니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = """
                                                    {
                                                        "code": "AUTH-010",
                                                        "message": "토큰이 만료되었습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> registerFcmToken(
            Long memberId,
            @RequestBody(
                    description = "FCM 토큰 등록 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterFcmTokenRequest.class)
                    )
            )
            RegisterFcmTokenRequest request
    );

    @Operation(
            summary = "FCM 토큰 삭제",
            description = "디바이스의 FCM 토큰을 삭제합니다. 앱 로그아웃 시 호출합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "인증 정보 없음",
                                            value = """
                                                    {
                                                        "code": "AUTH-007",
                                                        "message": "로그인이 필요합니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 무효",
                                            value = """
                                                    {
                                                        "code": "AUTH-009",
                                                        "message": "유효하지 않은 토큰입니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = """
                                                    {
                                                        "code": "AUTH-010",
                                                        "message": "토큰이 만료되었습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
    })
    ResponseEntity<Void> deleteFcmToken(
            Long memberId,
            @RequestBody(
                    description = "FCM 토큰 삭제 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DeleteFcmTokenRequest.class)
                    )
            )
            DeleteFcmTokenRequest request
    );

    @Operation(
            summary = "알림 설정 저장",
            description = """
                    질문 리마인더 알림 설정을 등록하거나 업데이트합니다. (Upsert)
                    - alarmTime: 디바이스 로컬 타임 기준 HH:mm 형식 (예: 08:00)
                    - timezone: IANA 타임존 ID (예: Asia/Seoul, America/New_York)
                    - enabled: false로 설정하면 알림이 비활성화되고, true로 설정하면 활성화됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "저장 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetQuestionReminderSettingResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "alarmTime 형식 오류",
                                    value = """
                                            {
                                                "code": "COMMON-001",
                                                "message": "알람 시간은 HH:mm 형식이어야 합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "인증 정보 없음",
                                            value = """
                                                    {
                                                        "code": "AUTH-007",
                                                        "message": "로그인이 필요합니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 무효",
                                            value = """
                                                    {
                                                        "code": "AUTH-009",
                                                        "message": "유효하지 않은 토큰입니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = """
                                                    {
                                                        "code": "AUTH-010",
                                                        "message": "토큰이 만료되었습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ResponseEntity<SetQuestionReminderSettingResponse> upsertSetting(
            Long memberId,
            @RequestBody(
                    description = "알림 설정 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetQuestionReminderSettingRequest.class)
                    )
            )
            SetQuestionReminderSettingRequest request
    );

    @Operation(
            summary = "알림 설정 조회",
            description = "현재 로그인한 회원의 질문 리마인더 알림 설정을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetQuestionReminderSettingResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "인증 정보 없음",
                                            value = """
                                                    {
                                                        "code": "AUTH-007",
                                                        "message": "로그인이 필요합니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 무효",
                                            value = """
                                                    {
                                                        "code": "AUTH-009",
                                                        "message": "유효하지 않은 토큰입니다."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "토큰 만료",
                                            value = """
                                                    {
                                                        "code": "AUTH-010",
                                                        "message": "토큰이 만료되었습니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "알림 설정 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "NOTIFICATION-001",
                                                "message": "알림 설정을 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<SetQuestionReminderSettingResponse> getSetting(Long memberId);
}
