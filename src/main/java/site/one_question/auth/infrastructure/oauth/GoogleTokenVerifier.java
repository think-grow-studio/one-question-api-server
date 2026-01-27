package site.one_question.auth.infrastructure.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.one_question.auth.domain.exception.GoogleTokenVerificationException;

@Component
@Slf4j
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken == null) {
                throw new GoogleTokenVerificationException("Invalid Google ID Token");
            }
            return googleIdToken.getPayload();
        } catch (Exception e) {
            log.error("Google ID Token 검증 실패: {}", e.getMessage());
            throw new GoogleTokenVerificationException(e.getMessage());
        }
    }
}
