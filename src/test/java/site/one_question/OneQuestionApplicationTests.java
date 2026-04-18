package site.one_question;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import site.one_question.integrate.test_config.IntegrateTestConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrateTestConfig.class)
class OneQuestionApplicationTests {

	@Test
	void contextLoads() {
	}

}
