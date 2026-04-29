package site.one_question.api.auth.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import site.one_question.api.auth.presentation.request.AnonymousAuthRequest;
import site.one_question.api.auth.presentation.request.AppleAuthRequest;
import site.one_question.api.auth.presentation.request.CheckAppleLinkRequest;
import site.one_question.api.auth.presentation.request.GoogleAuthRequest;
import site.one_question.api.auth.presentation.request.CheckGoogleLinkRequest;
import site.one_question.api.auth.presentation.request.LinkToAppleRequest;
import site.one_question.api.auth.presentation.request.LinkToGoogleRequest;
import site.one_question.api.auth.presentation.request.ReissueAuthTokenRequest;
import site.one_question.api.auth.presentation.response.AuthResponse;
import site.one_question.api.auth.presentation.response.CheckAppleLinkResponse;
import site.one_question.api.auth.presentation.response.CheckGoogleLinkResponse;
import site.one_question.api.auth.presentation.response.ReissueAuthTokenResponse;

@Tag(name = "Auth", description = "인증 관련 API")
public interface AuthApi {

    @Operation(
            summary = "Google OAuth 인증",
            description = """
                    Google ID 토큰을 검증하고 JWT 토큰을 발급합니다.
                    신규 사용자의 경우 자동으로 회원가입이 진행됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "인증 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Google 토큰 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-004",
                                                "message": "Google 인증에 실패했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<AuthResponse> googleAuth(
            @Parameter(
                    name = "Accept-Language",
                    description = "클라이언트 로케일 (예: ko-KR)",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "ko-KR"
            )
            String locale,
            @Parameter(
                    name = "Timezone",
                    description = "클라이언트 타임존 (예: Asia/Seoul)",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "Asia/Seoul"
            )
            String timezone,
            @RequestBody(
                    description = "Google OAuth 인증 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleAuthRequest.class)
                    )
            )
            GoogleAuthRequest request
    );

    @Operation(
            summary = "Apple OAuth 인증",
            description = """
                    Apple Identity 토큰을 검증하고 JWT 토큰을 발급합니다.
                    신규 사용자의 경우 자동으로 회원가입이 진행됩니다.

                    [클라이언트 가이드]
                    - 사용자 이름(name): 최초 로그인 시에만 Apple로부터 제공되므로 optional.
                    - rawNonce(필수): 클라이언트는 랜덤 문자열(rawNonce)을 만들어
                      SHA256 hex(lowercase)를 expo-apple-authentication의 `nonce` 옵션에 넘기고,
                      서버에는 rawNonce를 그대로 전송합니다. 서버는 토큰의 nonce claim과 비교하여 replay를 방지합니다.
                    - authorizationCode(필수): 회원 탈퇴 시 Apple 권한을 revoke하기 위해 함께 전송합니다.
                      신규 가입 또는 refresh_token 미보유 회원에 한해 서버가 Apple `/auth/token`과 교환해 저장합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "인증 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Apple 토큰 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-005",
                                                "message": "Apple 인증에 실패했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<AuthResponse> appleAuth(
            @Parameter(
                    name = "Accept-Language",
                    description = "클라이언트 로케일 (예: ko-KR)",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "ko-KR"
            )
            String locale,
            @Parameter(
                    name = "Timezone",
                    description = "클라이언트 타임존 (예: Asia/Seoul)",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "Asia/Seoul"
            )
            String timezone,
            @RequestBody(
                    description = "Apple OAuth 인증 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppleAuthRequest.class)
                    )
            )
            AppleAuthRequest request
    );

    @Operation(
            summary = "익명 인증",
            description = """
                    Firebase 익명 인증 토큰을 검증하고 JWT 토큰을 발급합니다.
                    신규 사용자의 경우 자동으로 회원가입이 진행됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "인증 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Firebase 토큰 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-011",
                                                "message": "Firebase 인증에 실패했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<AuthResponse> anonymousAuth(
            @Parameter(
                    name = "Accept-Language",
                    description = "클라이언트 로케일 (예: ko-KR)",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "ko-KR"
            )
            String locale,
            @Parameter(
                    name = "Timezone",
                    description = "클라이언트 타임존 (예: Asia/Seoul)",
                    in = ParameterIn.HEADER,
                    required = true,
                    example = "Asia/Seoul"
            )
            String timezone,
            @RequestBody(
                    description = "익명 인증 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnonymousAuthRequest.class)
                    )
            )
            AnonymousAuthRequest request
    );

    @Operation(
            summary = "Google 계정 연결 확인",
            description = "Google ID 토큰을 검증하여 해당 Google 계정이 이미 존재하는지 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "확인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CheckGoogleLinkResponse.class)
                    )
            )
    })
    ResponseEntity<CheckGoogleLinkResponse> checkGoogleLinking(Long memberId, CheckGoogleLinkRequest request);

    @Operation(
            summary = "Google 계정 연결",
            description = """
                    익명 사용자의 계정을 Google 계정으로 연결합니다.
                    연결 후 새로운 JWT 토큰이 발급됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "연결 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 연동된 계정 (익명이 아닌 사용자)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-012",
                                                "message": "이미 연동된 계정입니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 Google 계정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-013",
                                                "message": "이미 사용 중인 Google 계정입니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<AuthResponse> linkToGoogle(Long memberId, LinkToGoogleRequest request);

    @Operation(
            summary = "Apple 계정 연결 확인",
            description = """
                    Apple Identity 토큰을 검증하여 해당 Apple 계정이 이미 우리 시스템에 가입되어 있는지 확인합니다.
                    익명 사용자가 Apple 연동 전에 호출하여 "기존 계정으로 이어서 로그인할지" UI 분기에 사용합니다.

                    rawNonce(필수): /auth/apple와 동일한 nonce 흐름. 클라이언트가 SHA256 hex를 Apple에 nonce로 넘기고
                    서버에 rawNonce를 함께 전송하여 replay 공격을 방지합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "확인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CheckAppleLinkResponse.class)
                    )
            )
    })
    ResponseEntity<CheckAppleLinkResponse> checkAppleLinking(Long memberId, CheckAppleLinkRequest request);

    @Operation(
            summary = "Apple 계정 연결",
            description = """
                    익명 사용자의 계정을 Apple 계정으로 연결합니다.
                    연결 후 새로운 JWT 토큰이 발급됩니다.

                    [클라이언트 가이드]
                    - 사용자 이름(name): 최초 로그인 시에만 Apple로부터 제공되므로 optional.
                    - rawNonce(필수): /auth/apple와 동일한 nonce 흐름. SHA256 hex를 Apple에 nonce로 넘기고
                      서버에는 rawNonce를 그대로 전송합니다.
                    - authorizationCode(필수): 회원 탈퇴 시 Apple 권한을 revoke하기 위해 함께 전송합니다.
                      서버가 Apple `/auth/token`과 교환해 refresh_token을 저장합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "연결 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 연동된 계정 (익명이 아닌 사용자)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-012",
                                                "message": "이미 연동된 계정입니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 Apple 계정",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-014",
                                                "message": "이미 사용 중인 Apple 계정입니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<AuthResponse> linkToApple(Long memberId, LinkToAppleRequest request);

    @Operation(
            summary = "토큰 재발급",
            description = """
                    리프레시 토큰을 사용하여 새로운 액세스 토큰과 리프레시 토큰을 발급받습니다.
                    기존 리프레시 토큰은 무효화됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReissueAuthTokenResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "리프레시 토큰 관련 인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "토큰 형식 오류",
                                            value = """
                                                    {
                                                        "code": "AUTH-001",
                                                        "message": "로그인을 다시 해주세요."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "리프레시 토큰 없음",
                                            value = """
                                                    {
                                                        "code": "AUTH-002",
                                                        "message": "로그인을 다시 해주세요."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "리프레시 토큰 만료",
                                            value = """
                                                    {
                                                        "code": "AUTH-003",
                                                        "message": "로그인을 다시 해주세요."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "리프레시 토큰 불일치",
                                            value = """
                                                    {
                                                        "code": "AUTH-006",
                                                        "message": "로그인을 다시 해주세요."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ReissueAuthTokenResponse> reissueToken(
            @RequestBody(
                    description = "토큰 재발급 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReissueAuthTokenRequest.class)
                    )
            )
            ReissueAuthTokenRequest request
    );

    @Operation(
            summary = "로그아웃",
            description = "현재 사용자의 리프레시 토큰을 무효화합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "로그아웃 성공"
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
    ResponseEntity<Void> logout(Long memberId);

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    현재 사용자의 계정을 즉시 삭제하고 모든 세션을 무효화합니다.

                    Apple 로그인 사용자의 경우, 저장된 refresh_token으로 Apple `/auth/revoke`를 호출하여
                    Apple 측 사용자-앱 권한 연결도 함께 해제합니다 (App Store Review Guideline 5.1.1(v) 대응).
                    Apple revoke가 네트워크/서버 사정으로 실패해도 회원 탈퇴 자체는 정상 진행됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "회원 탈퇴 성공"
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
                    description = "회원을 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "MEMBER-001",
                                                "message": "회원 정보를 찾을 수 없습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<Void> withdraw(Long memberId);
}
