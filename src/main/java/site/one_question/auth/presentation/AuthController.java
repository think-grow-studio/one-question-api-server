package site.one_question.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import site.one_question.auth.application.AuthApplicationService;
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

    private final AuthApplicationService authApplicationService;

    @Override
    @PostMapping("/google")
    public AuthResponse googleAuth(
            @RequestHeader("Accept-Language") String locale,
            @RequestHeader("Timezone") String timezone,
            @Valid @RequestBody GoogleAuthRequest request
    ) {
        return authApplicationService.googleAuth(request, locale, timezone);
    }

    @Override
    @PostMapping("/apple")
    public AuthResponse appleAuth(
            @RequestHeader("Accept-Language") String locale,
            @RequestHeader("Timezone") String timezone,
            @Valid @RequestBody AppleAuthRequest request
    ) {
        return authApplicationService.appleAuth(request, locale, timezone);
    }

    @Override
    @PostMapping("/reissue-token")
    public ReissueAuthTokenResponse reissueToken(@Valid @RequestBody ReissueAuthTokenRequest request) {
        return authApplicationService.reissueToken(request);
    }

    @Override
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@PrincipalId Long memberId) {
        authApplicationService.logout(memberId);
    }
}
