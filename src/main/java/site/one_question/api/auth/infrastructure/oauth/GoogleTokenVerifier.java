package site.one_question.api.auth.infrastructure.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.one_question.api.auth.domain.exception.GoogleTokenVerificationException;

@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdToken.Payload verify(String idToken) {
        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (Exception e) {
            log.error("Google ID Token 검증 실패: {}", e.getClass().getSimpleName());
            throw new GoogleTokenVerificationException(e);
        }
        if (googleIdToken == null) {
            throw new GoogleTokenVerificationException("Invalid Google ID Token");
        }
        return googleIdToken.getPayload();
    }
}
