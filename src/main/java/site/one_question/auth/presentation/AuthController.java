package site.one_question.auth.presentation;

import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.auth.application.AuthApplication;
import site.one_question.auth.infrastructure.annotation.PrincipalId;
import site.one_question.auth.presentation.request.AppleAuthRequest;
import site.one_question.auth.presentation.request.GoogleAuthRequest;
import site.one_question.auth.presentation.request.ReissueAuthTokenRequest;
import site.one_question.auth.presentation.response.AuthResponse;
import site.one_question.auth.presentation.response.ReissueAuthTokenResponse;
import site.one_question.global.common.HttpHeaderConstant;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthApplication authApplication;

    @Override
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(
            @RequestHeader(ACCEPT_LANGUAGE) String locale,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone,
            @Valid @RequestBody GoogleAuthRequest request
    ) {
        log.info("[API] 구글 로그인 요청 시작");
        AuthResponse response = authApplication.googleAuth(request, locale, timezone);
        log.info("[API] 구글 로그인 요청 종료 - isNewMember: {}", response.isNewMember());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/apple")
    public ResponseEntity<AuthResponse> appleAuth(
            @RequestHeader(ACCEPT_LANGUAGE) String locale,
            @RequestHeader(HttpHeaderConstant.TIMEZONE) String timezone,
            @Valid @RequestBody AppleAuthRequest request
    ) {
        log.info("[API] 애플 로그인 요청 시작");
        AuthResponse response = authApplication.appleAuth(request, locale, timezone);
        log.info("[API] 애플 로그인 요청 종료 - isNewMember: {}", response.isNewMember());
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/reissue-token")
    public ResponseEntity<ReissueAuthTokenResponse> reissueToken(@Valid @RequestBody ReissueAuthTokenRequest request) {
        log.info("[API] 토큰 재발급 요청 시작");
        ReissueAuthTokenResponse response = authApplication.reissueToken(request);
        log.info("[API] 토큰 재발급 요청 종료");
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@PrincipalId Long memberId) {
        log.info("[API] 로그아웃 요청 시작");
        authApplication.logout(memberId);
        log.info("[API] 로그아웃 요청 종료");
        return ResponseEntity.noContent().build();
    }
}
