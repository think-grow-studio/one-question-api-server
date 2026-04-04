package site.one_question.test_config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.auth.infrastructure.oauth.AppleTokenVerifier;
import site.one_question.api.auth.infrastructure.oauth.FirebaseTokenVerifier;
import site.one_question.api.auth.infrastructure.oauth.GoogleTokenVerifier;
import site.one_question.api.answerpost.domain.AnswerPostLikeRepository;
import site.one_question.api.answerpost.domain.AnswerPostRepository;
import site.one_question.api.question.domain.QuestionLikeRepository;
import site.one_question.api.auth.domain.RefreshTokenRepository;
import site.one_question.api.member.domain.MemberRepository;
import site.one_question.api.question.domain.DailyQuestionAnswerRepository;
import site.one_question.api.question.domain.DailyQuestionRepository;
import site.one_question.api.question.domain.QuestionCycleRepository;
import site.one_question.api.question.domain.QuestionRepository;
import site.one_question.test_config.utils.TestAnswerPostLikeUtils;
import site.one_question.test_config.utils.TestAnswerPostUtils;
import site.one_question.test_config.utils.TestQuestionLikeUtils;
import site.one_question.test_config.utils.TestAuthUtils;
import site.one_question.test_config.utils.TestDailyQuestionAnswerUtils;
import site.one_question.test_config.utils.TestDailyQuestionUtils;
import site.one_question.test_config.utils.TestMemberUtils;
import site.one_question.test_config.utils.TestQuestionCycleUtils;
import site.one_question.test_config.utils.TestQuestionUtils;
import site.one_question.test_config.utils.TestRefreshTokenUtils;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrateTestConfig.class)
public abstract class IntegrateTest {

    // 테스트 핵심 도구
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // Repositories
    @Autowired
    protected MemberRepository memberRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected QuestionRepository questionRepository;

    @Autowired
    protected QuestionCycleRepository questionCycleRepository;

    @Autowired
    protected DailyQuestionRepository dailyQuestionRepository;

    @Autowired
    protected DailyQuestionAnswerRepository dailyQuestionAnswerRepository;

    @Autowired
    protected AnswerPostRepository answerPostRepository;

    @Autowired
    protected AnswerPostLikeRepository answerPostLikeRepository;

    @Autowired
    protected QuestionLikeRepository questionLikeRepository;

    // Test Utils
    @Autowired
    protected TestMemberUtils testMemberUtils;

    @Autowired
    protected TestAuthUtils testAuthUtils;

    @Autowired
    protected TestQuestionUtils testQuestionUtils;

    @Autowired
    protected TestQuestionCycleUtils testQuestionCycleUtils;

    @Autowired
    protected TestDailyQuestionUtils testDailyQuestionUtils;

    @Autowired
    protected TestDailyQuestionAnswerUtils testDailyQuestionAnswerUtils;

    @Autowired
    protected TestRefreshTokenUtils testRefreshTokenUtils;

    @Autowired
    protected TestAnswerPostUtils testAnswerPostUtils;

    @Autowired
    protected TestAnswerPostLikeUtils testAnswerPostLikeUtils;

    @Autowired
    protected TestQuestionLikeUtils testQuestionLikeUtils;

    // 트랜잭션 관리
    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    // Mock Verifiers
    @Autowired
    protected GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    protected AppleTokenVerifier appleTokenVerifier;

    @Autowired
    protected FirebaseTokenVerifier firebaseTokenVerifier;

    // API URL 상수
    protected static final String API_V1 = "/api/v1";
    protected static final String AUTH_API = API_V1 + "/auth";
    protected static final String MEMBERS_API = API_V1 + "/members";
    protected static final String QUESTIONS_API = API_V1 + "/questions";
    protected static final String ANSWER_POSTS_API = API_V1 + "/answer-posts";

    @BeforeEach
    void resetMocks() {
        Mockito.reset(googleTokenVerifier, appleTokenVerifier, firebaseTokenVerifier);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
            entityManager.getMetamodel().getEntities().stream()
                .forEach(entityType -> {
                    entityManager.createQuery("DELETE FROM " + entityType.getName()).executeUpdate();
                });
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return null;
        });
    }
}
