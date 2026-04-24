package site.one_question.api.auth.infrastructure.oauth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.one_question.api.auth.domain.exception.FirebaseTokenVerificationException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public record FirebaseTokenPayload(String uid) {}

    public FirebaseTokenPayload verify(String idToken) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            return new FirebaseTokenPayload(decoded.getUid());
        } catch (Exception e) {
            log.error("Firebase ID Token 검증 실패: {}", e.getMessage());
            throw new FirebaseTokenVerificationException(e.getMessage());
        }
    }
}
