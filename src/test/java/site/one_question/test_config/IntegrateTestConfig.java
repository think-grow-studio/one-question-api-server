package site.one_question.test_config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import site.one_question.auth.infrastructure.oauth.AppleTokenVerifier;
import site.one_question.auth.infrastructure.oauth.GoogleTokenVerifier;

@TestConfiguration
public class IntegrateTestConfig {

    @Bean
    @Primary
    public GoogleTokenVerifier mockGoogleTokenVerifier() {
        return Mockito.mock(GoogleTokenVerifier.class);
    }

    @Bean
    @Primary
    public AppleTokenVerifier mockAppleTokenVerifier() {
        return Mockito.mock(AppleTokenVerifier.class);
    }
}
