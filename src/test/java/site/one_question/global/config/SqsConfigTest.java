package site.one_question.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.one_question.config.SqsConfig;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;

class SqsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SqsConfig.class)
            .withPropertyValues(
                    "app.aws.region=ap-northeast-2",
                    "app.aws.sqs.api-call-timeout-ms=20000",
                    "app.aws.sqs.api-call-attempt-timeout-ms=10000");

    @Test
    void sqs_client_timeouts_finish_before_claim_lease() {
        contextRunner.run(context -> {
            SqsClient client = context.getBean(SqsClient.class);
            ClientOverrideConfiguration override =
                    client.serviceClientConfiguration().overrideConfiguration();

            assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(20));
            assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(10));
        });
    }
}
