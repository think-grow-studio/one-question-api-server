package site.one_question.global.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

@Slf4j
@Configuration
@Profile("!test")
public class FirebaseConfig {

    @Value("${firebase.service-account-json}")
    private String serviceAccountJsonBase64;

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        Assert.hasText(serviceAccountJsonBase64,
                "firebase.service-account-json 환경변수가 비어있습니다");

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(serviceAccountJsonBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "firebase.service-account-json base64 디코딩 실패", e);
        }

        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(decoded)
        );

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(options);
        log.info("Firebase 초기화 완료");
        return firebaseApp;
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
