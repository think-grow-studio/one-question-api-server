package site.one_question.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(
            @Value("${app.aws.region:ap-northeast-2}") String region,
            @Value("${app.aws.sqs.api-call-timeout-ms:20000}") long apiCallTimeoutMs,
            @Value("${app.aws.sqs.api-call-attempt-timeout-ms:6000}") long apiCallAttemptTimeoutMs
    ) {
        ClientOverrideConfiguration overrideConfiguration =
                ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMillis(apiCallTimeoutMs))
                        .apiCallAttemptTimeout(Duration.ofMillis(apiCallAttemptTimeoutMs))
                        .build();
        return SqsClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(overrideConfiguration)
                .build();
    }
}
