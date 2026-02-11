package site.one_question.auth.presentation;

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
import site.one_question.auth.presentation.request.AppleAuthRequest;
import site.one_question.auth.presentation.request.GoogleAuthRequest;
import site.one_question.auth.presentation.request.ReissueAuthTokenRequest;
import site.one_question.auth.presentation.response.AuthResponse;
import site.one_question.auth.presentation.response.ReissueAuthTokenResponse;

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
                    Apple의 경우 사용자 이름은 최초 로그인 시에만 제공됩니다.
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
                    description = "리프레시 토큰이 유효하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "AUTH-001",
                                                "message": "인증 토큰이 유효하지 않습니다."
                                            }
                                            """
                            )
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
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "UNAUTHORIZED",
                                                "message": "인증이 필요합니다."
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<Void> logout(Long memberId);

    @Operation(
            summary = "회원 탈퇴",
            description = "현재 사용자의 계정을 즉시 삭제하고 모든 세션을 무효화합니다."
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
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "code": "UNAUTHORIZED",
                                                "message": "인증이 필요합니다."
                                            }
                                            """
                            )
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
