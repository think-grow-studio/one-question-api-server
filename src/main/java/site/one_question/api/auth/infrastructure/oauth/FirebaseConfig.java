package site.one_question.api.auth.infrastructure.oauth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-json}")
    private String serviceAccountJsonBase64;

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (serviceAccountJsonBase64 == null || serviceAccountJsonBase64.isBlank()) {
                log.warn("Firebase service account JSON이 설정되지 않았습니다. Firebase 기능이 비활성화됩니다.");
                return;
            }
            if (FirebaseApp.getApps().isEmpty()) {
                byte[] decoded = Base64.getDecoder().decode(serviceAccountJsonBase64);
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(decoded)
                );
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase 초기화 완료");
            }
        } catch (Exception e) {
            log.error("Firebase 초기화 실패: {}", e.getMessage(), e);
        }
    }
}
