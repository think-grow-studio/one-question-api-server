package site.one_question.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthApplication authApplication;

    @Override
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleAuth(
            @RequestHeader("Accept-Language") String locale,
            @RequestHeader("Timezone") String timezone,
            @Valid @RequestBody GoogleAuthRequest request
    ) {
        AuthResponse response = authApplication.googleAuth(request, locale, timezone);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/apple")
    public ResponseEntity<AuthResponse> appleAuth(
            @RequestHeader("Accept-Language") String locale,
            @RequestHeader("Timezone") String timezone,
            @Valid @RequestBody AppleAuthRequest request
    ) {
        AuthResponse response = authApplication.appleAuth(request, locale, timezone);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/reissue-token")
    public ResponseEntity<ReissueAuthTokenResponse> reissueToken(@Valid @RequestBody ReissueAuthTokenRequest request) {
        ReissueAuthTokenResponse response = authApplication.reissueToken(request);
        return ResponseEntity.ok(response);
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@PrincipalId Long memberId) {
        authApplication.logout(memberId);
        return ResponseEntity.noContent().build();
    }
}
