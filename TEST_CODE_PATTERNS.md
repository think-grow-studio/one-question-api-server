# Spring Boot 통합 테스트 패턴 가이드

> **대상 프로젝트**: One Question API Server (Spring Boot 3.4.1 / Java 21)
>
> **목적**: 이 프로젝트의 실제 통합 테스트 구조와 패턴을 정리한 문서입니다. 새 도메인/기능을 추가할 때 동일한 패턴으로 테스트를 작성하기 위한 기준으로 사용합니다.
>
> 이 문서의 모든 예제는 `src/test/java/site/one_question` 의 실제 코드를 기반으로 합니다.

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [디렉토리 구조](#2-디렉토리-구조)
3. [핵심 파일 상세 설명](#3-핵심-파일-상세-설명)
4. [테스트 유틸리티 클래스](#4-테스트-유틸리티-클래스)
5. [실제 테스트 케이스 작성법](#5-실제-테스트-케이스-작성법)
6. [명명 규칙](#6-명명-규칙)
7. [새 도메인/테스트 추가 가이드](#7-새-도메인테스트-추가-가이드)

---

## 1. 아키텍처 개요

### 1.1 테스트 전략

이 프로젝트는 **통합 테스트(Integration Test)** 중심의 테스트 전략을 사용합니다. `MockMvc`로 실제 HTTP 요청을 보내고, 실제 H2 DB에 커밋된 결과를 검증합니다.

```
┌───────────────────────────────────────────────────────────────────┐
│                         IntegrateTest (abstract)                  │
│  (모든 통합 테스트의 부모 클래스)                                    │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ ┌────────┐ │
│  │   MockMvc    │  │ Repositories │  │ TestXxxUtils │ │ Mock   │ │
│  │  (API 호출)  │  │  (DB 직접접근)│  │ (데이터 생성) │ │Verifier│ │
│  └──────────────┘  └──────────────┘  └──────────────┘ └────────┘ │
│                                                                   │
│  @BeforeEach resetMocks() / resetLockProvider()                   │
│  @AfterEach  tearDown() - 매 테스트 후 전체 DB 초기화              │
└───────────────────────────────────────────────────────────────────┘
                              │ extends
                              ▼
┌───────────────────────────────────────────────────────────────────┐
│              도메인별 통합 테스트 클래스들                          │
│  ServeDailyQuestionIntegrateTest, AnonymousAuthIntegrateTest, ...  │
│  (대부분 @Nested 로 시나리오 그룹화)                                │
└───────────────────────────────────────────────────────────────────┘
```

### 1.2 핵심 설계 원칙

| 원칙 | 설명 | 구현 방법 |
|------|------|----------|
| **테스트 격리** | 각 테스트는 독립적으로 실행 가능해야 함 | `@AfterEach` tearDown에서 모든 테이블 데이터 삭제 |
| **데이터 생성 재사용** | 테스트 데이터 생성 로직 중복 방지 | `test_config/utils` 의 `TestXxxUtils` 컴포넌트 |
| **외부 서비스 분리** | OAuth/Firebase 검증기, 메시징은 Mock 처리 | `IntegrateTestConfig`에서 `@Primary` Mock Bean 등록 |
| **실제 DB 사용** | H2 인메모리 DB로 실제 쿼리 검증 | `@ActiveProfiles("test")` + `application-test.properties` |
| **스케줄러 락 초기화** | ShedLock 상태가 테스트 간 누수되지 않도록 | `ResettableInMemoryLockProvider` 를 매 테스트 reset |

> 참고: 컨트롤러는 `ApiResponse<T>` 같은 공통 래퍼 없이 **`ResponseEntity<DTO>`를 직접 반환**합니다. 따라서 응답 검증은 `jsonPath("$.필드")`로 DTO 필드에 바로 접근합니다 (예: `$.likeCount`, `$.candidates[0].likeCount`).

---

## 2. 디렉토리 구조

```
src/test/java/site/one_question/
│
├── integrate/                                # [핵심] 통합 테스트 루트
│   │
│   ├── test_config/                          # [필수] 테스트 설정 패키지
│   │   ├── IntegrateTest.java                # [핵심] 모든 통합 테스트의 추상 부모 클래스
│   │   ├── IntegrateTestConfig.java          # [핵심] 외부 서비스 Mock Bean 설정
│   │   ├── ResettableInMemoryLockProvider.java  # ShedLock 테스트용 락 프로바이더
│   │   │
│   │   ├── initializer/
│   │   │   └── QuestionJsonDto.java          # 시드 데이터 초기화용 DTO
│   │   │
│   │   └── utils/                            # 테스트 데이터 생성 유틸리티 모음
│   │       ├── TestMemberUtils.java
│   │       ├── TestAuthUtils.java
│   │       ├── TestQuestionUtils.java
│   │       ├── TestQuestionCycleUtils.java
│   │       ├── TestDailyQuestionUtils.java
│   │       ├── TestDailyQuestionAnswerUtils.java
│   │       ├── TestQuestionLikeUtils.java
│   │       ├── TestAnswerPostUtils.java
│   │       ├── TestAnswerPostLikeUtils.java
│   │       ├── TestFcmTokenUtils.java
│   │       ├── TestQuestionReminderSettingUtils.java
│   │       └── TestRefreshTokenUtils.java
│   │
│   ├── auth/                                 # 인증 도메인 테스트
│   │   ├── AnonymousAuthIntegrateTest.java
│   │   ├── AuthSignupIntegrateTest.java
│   │   ├── AuthLogoutIntegrateTest.java
│   │   ├── AuthWithdrawIntegrateTest.java
│   │   ├── CheckGoogleLinkIntegrateTest.java
│   │   └── LinkToGoogleIntegrateTest.java
│   │
│   ├── member/                               # 회원 도메인 테스트
│   │   ├── GetMemberIntegrateTest.java
│   │   └── UpdateMemberIntegrateTest.java
│   │
│   ├── question/                             # 질문 도메인 테스트
│   │   ├── ServeDailyQuestionIntegrateTest.java
│   │   ├── ReloadDailyQuestionIntegrateTest.java
│   │   ├── SelectQuestionIntegrateTest.java
│   │   ├── CreateAnswerIntegrateTest.java
│   │   ├── UpdateAnswerIntegrateTest.java
│   │   ├── GetQuestionHistoryIntegrateTest.java
│   │   ├── GetQuestionTimelineIntegrateTest.java
│   │   └── ToggleLikeQuestionIntegrateTest.java
│   │
│   ├── answerpost/                           # 답변 게시글(피드) 도메인 테스트
│   │   ├── GetAnswerPostFeedIntegrateTest.java
│   │   └── ToggleLikeAnswerPostIntegrateTest.java
│   │
│   ├── publicquestion/                       # 공개 질문/답변 도메인 테스트
│   │   ├── GetPublicDailyQuestionIntegrateTest.java
│   │   ├── GetPublicDailyQuestionAnswersIntegrateTest.java
│   │   ├── CreatePublicDailyQuestionAnswerIntegrateTest.java
│   │   └── ...
│   │
│   └── notification/                         # 알림/FCM/리마인더 도메인 테스트
│       ├── FcmTokenRegisterIntegrateTest.java
│       ├── QuestionReminderSettingUpsertIntegrateTest.java
│       └── QuestionRemindSchedulerIntegrateTest.java
│
├── domain/                                   # 순수 단위 테스트 (Spring Context 없이)
│   └── question/DailyQuestionServiceTest.java
│
└── global/                                   # 글로벌 설정 단위 테스트
    └── config/MessageConfigTest.java
```

> **포인트**
> - 통합 테스트는 모두 `integrate/{도메인}/` 아래에 둡니다.
> - 테스트 설정/유틸은 `integrate/test_config/` 와 그 하위 `utils/`에 모읍니다.
> - 도메인 단위 테스트(Spring Context 불필요)는 `domain/` 아래에 별도로 둡니다.

---

## 3. 핵심 파일 상세 설명

### 3.1 IntegrateTest.java (가장 중요)

**위치**: `src/test/java/site/one_question/integrate/test_config/IntegrateTest.java`

**역할**: 모든 통합 테스트 클래스가 상속받는 **추상(abstract)** 부모 클래스. 공통 도구·Repository·TestUtils·Mock·URL 상수·DB 초기화 로직을 모두 제공합니다.

**전체 코드** (실제):

```java
package site.one_question.integrate.test_config;

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
// ... Repository / TestUtils / Mock Verifier import 들

@ActiveProfiles("test")            // application-test.properties 사용
@SpringBootTest                    // 전체 Spring Context 로드
@AutoConfigureMockMvc              // MockMvc 자동 구성
@Import(IntegrateTestConfig.class) // 테스트 전용 Mock Bean 설정 임포트
public abstract class IntegrateTest {

    // ===== 테스트 핵심 도구 =====
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    // ===== Repository 주입 (DB 직접 접근/검증용) =====
    @Autowired protected MemberRepository memberRepository;
    @Autowired protected RefreshTokenRepository refreshTokenRepository;
    @Autowired protected QuestionRepository questionRepository;
    @Autowired protected QuestionCycleRepository questionCycleRepository;
    @Autowired protected DailyQuestionRepository dailyQuestionRepository;
    @Autowired protected DailyQuestionAnswerRepository dailyQuestionAnswerRepository;
    @Autowired protected DailyQuestionCandidateRepository dailyQuestionCandidateRepository;
    @Autowired protected AnswerPostRepository answerPostRepository;
    @Autowired protected AnswerPostLikeRepository answerPostLikeRepository;
    @Autowired protected QuestionLikeRepository questionLikeRepository;
    @Autowired protected QuestionReminderSettingRepository questionReminderSettingRepository;
    @Autowired protected FcmTokenRepository fcmTokenRepository;

    // ===== 테스트 유틸리티 클래스 주입 =====
    @Autowired protected TestMemberUtils testMemberUtils;
    @Autowired protected TestAuthUtils testAuthUtils;
    @Autowired protected TestQuestionUtils testQuestionUtils;
    @Autowired protected TestQuestionCycleUtils testQuestionCycleUtils;
    @Autowired protected TestDailyQuestionUtils testDailyQuestionUtils;
    @Autowired protected TestDailyQuestionAnswerUtils testDailyQuestionAnswerUtils;
    @Autowired protected TestRefreshTokenUtils testRefreshTokenUtils;
    @Autowired protected TestAnswerPostUtils testAnswerPostUtils;
    @Autowired protected TestAnswerPostLikeUtils testAnswerPostLikeUtils;
    @Autowired protected TestQuestionLikeUtils testQuestionLikeUtils;
    @Autowired protected TestFcmTokenUtils testFcmTokenUtils;
    @Autowired protected TestQuestionReminderSettingUtils testQuestionReminderSettingUtils;

    // ===== 트랜잭션 관리 =====
    @PersistenceContext protected EntityManager entityManager;
    @Autowired protected TransactionTemplate transactionTemplate;

    @Autowired private ResettableInMemoryLockProvider resettableInMemoryLockProvider;

    // ===== 외부 인증 검증기 Mock (IntegrateTestConfig에서 @Primary로 주입) =====
    @Autowired protected GoogleTokenVerifier googleTokenVerifier;
    @Autowired protected AppleTokenVerifier appleTokenVerifier;
    @Autowired protected FirebaseTokenVerifier firebaseTokenVerifier;

    // ===== API URL 상수 (static final) =====
    protected static final String API_V1 = "/api/v1";
    protected static final String AUTH_API = API_V1 + "/auth";
    protected static final String MEMBERS_API = API_V1 + "/members";
    protected static final String QUESTIONS_API = API_V1 + "/questions";
    protected static final String ANSWER_POSTS_API = API_V1 + "/answer-posts";
    protected static final String PUBLIC_QUESTIONS_API = API_V1 + "/public-questions";
    protected static final String NOTIFICATION_API = API_V1 + "/members/me/notifications";
    protected static final String FCM_TOKEN_API = NOTIFICATION_API + "/fcm-token";
    protected static final String NOTIFICATION_SETTING_API = NOTIFICATION_API + "/settings";

    // ===== 매 테스트 전: Mock / 스케줄러 락 초기화 =====
    @BeforeEach
    void resetMocks() {
        Mockito.reset(googleTokenVerifier, appleTokenVerifier, firebaseTokenVerifier);
    }

    @BeforeEach
    void resetLockProvider() {
        resettableInMemoryLockProvider.reset();
    }

    // ===== 매 테스트 후: 전체 DB 초기화 (테스트 격리) =====
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
```

**어노테이션 설명**:

| 어노테이션 | 역할 |
|-----------|------|
| `@ActiveProfiles("test")` | `application-test.properties` 설정 사용 |
| `@SpringBootTest` | 전체 Spring Application Context 로드 (실제 Bean 사용) |
| `@AutoConfigureMockMvc` | MockMvc 자동 구성 (API 테스트용) |
| `@Import(IntegrateTestConfig.class)` | 테스트 전용 Mock Bean 설정 임포트 |

**`tearDown()` 메서드 포인트**:
- 각 테스트가 독립적으로 실행되도록 매 테스트 후 모든 테이블 데이터를 삭제합니다.
- `@Transactional` 롤백 방식 대신 **명시적 DELETE**를 사용합니다 → MockMvc 요청이 실제 커밋한 데이터까지 검증 가능.
- `SET REFERENTIAL_INTEGRITY FALSE/TRUE`는 **H2 전용 문법**입니다. (DB별 문법은 [부록](#부록-db별-외래-키-제약-비활성화-문법) 참고)
- `getMetamodel().getEntities()`로 모든 엔티티를 순회하므로, 새 엔티티가 추가돼도 tearDown 수정이 필요 없습니다.

**`@BeforeEach` 두 가지 초기화 포인트**:
- `resetMocks()`: 이전 테스트에서 stub된 Mock 검증기 상태를 초기화.
- `resetLockProvider()`: ShedLock 인메모리 락 상태를 초기화해 스케줄러 테스트 간 락 누수를 방지.

---

### 3.2 IntegrateTestConfig.java

**위치**: `src/test/java/site/one_question/integrate/test_config/IntegrateTestConfig.java`

**역할**: 외부 의존(OAuth 검증기, Firebase)을 Mock으로 대체하고, ShedLock 테스트용 락 프로바이더를 등록합니다.

**전체 코드** (실제):

```java
package site.one_question.integrate.test_config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import site.one_question.api.auth.infrastructure.oauth.AppleTokenVerifier;
import site.one_question.api.auth.infrastructure.oauth.FirebaseTokenVerifier;
import site.one_question.api.auth.infrastructure.oauth.GoogleTokenVerifier;

@TestConfiguration
public class IntegrateTestConfig {

    @Bean @Primary
    public GoogleTokenVerifier mockGoogleTokenVerifier() {
        return Mockito.mock(GoogleTokenVerifier.class);
    }

    @Bean @Primary
    public AppleTokenVerifier mockAppleTokenVerifier() {
        return Mockito.mock(AppleTokenVerifier.class);
    }

    @Bean @Primary
    public FirebaseTokenVerifier mockFirebaseTokenVerifier() {
        return Mockito.mock(FirebaseTokenVerifier.class);
    }

    @Bean @Primary
    public FirebaseMessaging mockFirebaseMessaging() {
        return Mockito.mock(FirebaseMessaging.class);
    }

    @Bean @Primary
    public FirebaseAuth mockFirebaseAuth() {
        return Mockito.mock(FirebaseAuth.class);
    }

    @Bean
    public ResettableInMemoryLockProvider resettableInMemoryLockProvider() {
        return new ResettableInMemoryLockProvider();
    }
}
```

**`@Primary`의 역할**: 실제 Bean과 Mock Bean이 동시에 존재할 때 Mock이 우선 주입됩니다.

**새 외부 의존을 추가할 때**: 위와 동일하게 `@Bean @Primary`로 Mock을 등록하고, `IntegrateTest`에 `@Autowired protected`로 필드를 추가한 뒤, 필요 시 `resetMocks()`의 `Mockito.reset(...)` 인자에 포함시킵니다.

---

## 4. 테스트 유틸리티 클래스

### 4.1 목적

테스트에서 반복되는 엔티티 생성/저장 로직을 `test_config/utils` 패키지의 `@Component`로 캡슐화합니다. 모두 `IntegrateTest`에 주입되어 모든 하위 테스트에서 바로 사용할 수 있습니다.

**장점**: 코드 중복 제거 / 엔티티 생성 시그니처 변경 시 한 곳만 수정 / 테스트 가독성 향상.

### 4.2 TestMemberUtils.java

**역할**: `Member` 엔티티 생성 및 저장. `static int uniqueId`로 유니크한 이메일/provider-id를 보장합니다.

```java
@Component
@RequiredArgsConstructor
public class TestMemberUtils {

    private final MemberRepository memberRepository;
    private static int uniqueId = 0;

    public Member createSave() {
        return createSave_With_Locale("ko-KR");
    }

    public Member createSave_With_Locale(String locale) {
        Member member = Member.create(
                "test" + uniqueId + "@test.com",
                "테스트유저" + uniqueId,
                AuthSocialProvider.GOOGLE,
                "provider-id-" + uniqueId++,
                locale,
                LocalDate.now(ZoneId.of("Asia/Seoul"))
        );
        return memberRepository.save(member);
    }

    public Member createSave_With_Email(String email) { /* ... */ }
    public Member createSave_Anonymous() { /* AuthSocialProvider.ANONYMOUS */ }
    public Member createSave_With_JoinedDate(LocalDate joinedDate) { /* ... */ }
}
```

> 회원 생성 시그니처: `Member.create(email, nickname, AuthSocialProvider, providerId, locale, joinedDate)`

### 4.3 TestAuthUtils.java

**역할**: 실제 `JwtService`로 AccessToken을 발급해 `"Bearer {token}"` 문자열을 만듭니다. MockMvc의 `Authorization` 헤더에 바로 넣어 사용합니다.

```java
@Component
@RequiredArgsConstructor
public class TestAuthUtils {

    private final JwtService jwtService;

    public String createBearerToken(Member member) {
        String token = jwtService.issueAccessToken(
                member.getId(),
                member.getEmail(),
                member.getPermission()
        );
        return "Bearer " + token;
    }
}
```

### 4.4 TestQuestionLikeUtils.java

**역할**: 질문 좋아요(`QuestionLike`) 생성. (특정 멤버가 특정 질문에 좋아요)

```java
@Component
@RequiredArgsConstructor
public class TestQuestionLikeUtils {

    private final QuestionLikeRepository repository;

    public QuestionLike createSave(Question question, Member member) {
        QuestionLike like = QuestionLike.create(question, member);
        return repository.save(like);
    }
}
```

### 4.5 TestDailyQuestionUtils.java

**역할**: `DailyQuestion` + 첫 후보(`DailyQuestionCandidate`)를 함께 생성합니다. 날짜/타임존 변형 메서드를 제공합니다.

```java
@Component
@RequiredArgsConstructor
public class TestDailyQuestionUtils {

    private final DailyQuestionRepository dailyQuestionRepository;
    private final DailyQuestionCandidateRepository candidateRepository;

    public DailyQuestion createSave(Member member, QuestionCycle cycle, Question question) {
        DailyQuestion dailyQuestion = DailyQuestion.create(
                member, cycle, question,
                LocalDate.now(ZoneId.of("Asia/Seoul")),
                "Asia/Seoul"
        );
        DailyQuestion saved = dailyQuestionRepository.save(dailyQuestion);
        candidateRepository.save(DailyQuestionCandidate.create(saved, question, 1));
        return saved;
    }

    public DailyQuestion createSave_With_Date(Member member, QuestionCycle cycle, Question question, LocalDate date) { /* ... */ }
    public DailyQuestion createSave_With_Timezone(Member member, QuestionCycle cycle, Question question, String timezone) { /* ... */ }
}
```

### 4.6 전체 유틸리티 목록 및 주요 메서드

| 클래스 | 주요 메서드 |
|--------|------------|
| **TestMemberUtils** | `createSave()`, `createSave_With_Locale(String)`, `createSave_With_Email(String)`, `createSave_Anonymous()`, `createSave_With_JoinedDate(LocalDate)` |
| **TestAuthUtils** | `createBearerToken(Member)` |
| **TestQuestionUtils** | `createSave()`, `createSave_With_Content(String)`, `createSave_With_QuestionNumber(int)`, `createSave_With_Locale(String)` |
| **TestQuestionCycleUtils** | `createSave(Member)`, `createSave_With_Timezone(Member, String)`, `createSave_With_StartDate(Member, LocalDate, String[, int])` |
| **TestDailyQuestionUtils** | `createSave(Member, QuestionCycle, Question)`, `createSave_With_Date(...)`, `createSave_With_Timezone(...)` |
| **TestDailyQuestionAnswerUtils** | `createSave(DailyQuestion, Member)`, `createSave_With_Content(...)`, `createSave_With_Timezone(...)` |
| **TestQuestionLikeUtils** | `createSave(Question, Member)` |
| **TestRefreshTokenUtils** | `createSave(Member, String, Instant)`, `createSave_Valid(Member, String)`, `createSave_Expired(Member, String)` |
| **TestAnswerPostUtils** | `createSave(DailyQuestionAnswer, Member)`, `createSave_Unpublished(...)`, `createSave_With_PostedAt(...)` |
| **TestAnswerPostLikeUtils** | `createSave(AnswerPost, Member)` |
| **TestFcmTokenUtils** | `createSave(Member, String)` |
| **TestQuestionReminderSettingUtils** | `createSave(Member, String, String)`, `createSave_Disabled(Member, String, String)` |

---

## 5. 실제 테스트 케이스 작성법

### 5.1 기본 조회 테스트 + `@Nested` 그룹화 (ServeDailyQuestionIntegrateTest)

**특징**: `@BeforeEach`에서 멤버·토큰·질문 풀을 준비하고, `@Nested`로 시나리오를 그룹화합니다. 응답은 `ResponseEntity<DTO>` 그대로이므로 `jsonPath("$.필드")`로 검증합니다.

```java
@DisplayName("오늘의 질문 조회 통합 테스트")
class ServeDailyQuestionIntegrateTest extends IntegrateTest {

    private static final String TIMEZONE = "Asia/Seoul";

    private Member member;
    private String token;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);

        // 질문 풀 확보 (DailyQuestion 생성 시 풀에서 선택)
        for (int i = 0; i < 10; i++) {
            testQuestionUtils.createSave();
        }
    }

    @Test
    @DisplayName("오늘의 질문 제공 시 200 OK 응답 및 질문 정보 반환")
    void serve_daily_question_with_valid_request_then_return_200_ok() throws Exception {
        // given
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

        // when & then
        mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyQuestionId").exists())
                .andExpect(jsonPath("$.questionCycle").value(1))
                .andExpect(jsonPath("$.candidates").isArray())
                .andExpect(jsonPath("$.candidates[0].selected").value(true));

        // DB 검증 - 실제 커밋된 데이터 확인 (AssertJ + .as() 설명 메시지)
        assertThat(dailyQuestionRepository.findAll())
                .as("DailyQuestion이 1개 생성되어야 함")
                .hasSize(1);
    }

    @Nested
    @DisplayName("멱등성 테스트")
    class IdempotencyTest {

        @Test
        @DisplayName("동일 날짜 2번 요청 시 같은 DailyQuestion 반환")
        void serve_same_date_twice_returns_same_daily_question() throws Exception {
            // ... 첫 요청 후 id 저장 → 두 번째 요청에서 동일 id 검증
        }
    }
}
```

**관례 포인트**:
- 인증이 필요한 API는 `.header(HttpHeaders.AUTHORIZATION, token)` 사용.
- 타임존은 `HttpHeaderConstant.TIMEZONE` 헤더로 전달.
- DB 검증 시 AssertJ의 `.as("...")`로 실패 메시지를 한국어로 명확히 남깁니다.

### 5.2 좋아요 수 시나리오 — `@Nested` 활용 예 (ServeDailyQuestionIntegrateTest)

기능별 시나리오는 `@Nested` 클래스로 묶습니다. 아래는 실제 `LikeCountTest` 그룹입니다.

```java
@Nested
@DisplayName("좋아요 수 테스트")
class LikeCountTest {

    @Test
    @DisplayName("좋아요가 없는 질문 조회 시 likeCount=0 반환")
    void serve_returns_likeCount_0_when_no_likes() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));

        mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.candidates[0].likeCount").value(0));
    }

    @Test
    @DisplayName("여러 멤버가 좋아요를 누른 경우 likeCount에 전체 합산 반영")
    void serve_returns_total_likeCount_across_members() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave();
        testDailyQuestionUtils.createSave(member, cycle, question);

        // 서로 다른 멤버 3명이 좋아요
        testQuestionLikeUtils.createSave(question, member);
        Member other1 = testMemberUtils.createSave();
        testQuestionLikeUtils.createSave(question, other1);
        Member other2 = testMemberUtils.createSave();
        testQuestionLikeUtils.createSave(question, other2);

        mockMvc.perform(get(QUESTIONS_API + "/daily/{date}", today)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaderConstant.TIMEZONE, TIMEZONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.candidates[0].likeCount").value(3));
    }
}
```

### 5.3 외부 인증 Mock 활용 테스트 (AnonymousAuthIntegrateTest)

**특징**: OAuth/Firebase 검증기는 Mock이므로 **BDDMockito `given(...).willReturn(...)`** 으로 토큰 검증 결과를 stub합니다.

```java
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@DisplayName("익명 인증 API 통합 테스트")
class AnonymousAuthIntegrateTest extends IntegrateTest {

    private final String AUTH_ANONYMOUS_URL = AUTH_API + "/anonymous";

    @Nested
    @DisplayName("성공 케이스")
    class SuccessTest {

        @Test
        @DisplayName("익명 회원가입 성공 시 회원이 생성되고 토큰이 반환된다")
        void anonymous_signup_creates_member_and_returns_tokens() throws Exception {
            // given - Firebase 토큰 검증 Mock stub
            String firebaseUid = "firebase-uid-abc123";
            given(firebaseTokenVerifier.verify(anyString()))
                    .willReturn(new FirebaseTokenPayload(firebaseUid));

            String requestBody = objectMapper.writeValueAsString(
                    new TestAnonymousAuthRequest("fake-firebase-id-token")
            );

            // when & then
            mockMvc.perform(post(AUTH_ANONYMOUS_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());
            // ... 토큰/회원 생성 검증
        }
    }
}
```

> Mock 상태는 부모의 `@BeforeEach resetMocks()`가 매 테스트마다 초기화하므로, 각 테스트는 자신이 필요한 stub만 선언하면 됩니다.

### 5.4 유효성 검사 — `@ParameterizedTest`

다양한 잘못된 입력을 한 메서드로 검증할 때 `@ParameterizedTest` + `@MethodSource`를 사용합니다.

```java
@ParameterizedTest(name = "[{index}] {0}")   // 테스트 이름에 첫 인자 표시
@MethodSource("provideInvalidRequests")
@DisplayName("유효하지 않은 입력이면 400 Bad Request")
void create_with_invalid_input_then_return_400(String testName, String content) throws Exception {
    mockMvc.perform(post(QUESTIONS_API + "/...")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CreateAnswerRequest(content))))
            .andExpect(status().isBadRequest());
}

private static Stream<Arguments> provideInvalidRequests() {
    return Stream.of(
            Arguments.of("내용이 null", null),
            Arguments.of("내용이 빈 문자열", ""),
            Arguments.of("내용 공백만", "   ")
    );
}
```

---

## 6. 명명 규칙

### 6.1 테스트 클래스 명명

```
{동작/기능}{대상}IntegrateTest

예시 (실제):
├── ServeDailyQuestionIntegrateTest      # 오늘의 질문 조회
├── ReloadDailyQuestionIntegrateTest     # 질문 리로드
├── SelectQuestionIntegrateTest          # 후보 질문 선택
├── GetQuestionHistoryIntegrateTest      # 질문 히스토리 조회
├── ToggleLikeQuestionIntegrateTest      # 질문 좋아요 토글
├── AnonymousAuthIntegrateTest           # 익명 인증
└── FcmTokenRegisterIntegrateTest        # FCM 토큰 등록
```

### 6.2 테스트 메서드 명명 (영어 스네이크 케이스)

```
{동작}_{조건/상황}_then_{예상결과}   또는   {주체}_{동작}_{결과}

예시 (실제):
✅ serve_daily_question_with_valid_request_then_return_200_ok
✅ serve_returns_likeCount_0_when_no_likes
✅ serve_returns_total_likeCount_across_members
✅ anonymous_signup_creates_member_and_returns_tokens
✅ serve_same_date_twice_returns_same_daily_question
```

### 6.3 `@DisplayName` 규칙 (한국어)

```java
@DisplayName("오늘의 질문 조회 통합 테스트")   // 클래스: 무엇을 테스트하는지
class ServeDailyQuestionIntegrateTest extends IntegrateTest {

    @Nested
    @DisplayName("좋아요 수 테스트")           // @Nested: 시나리오 그룹 이름
    class LikeCountTest {

        @Test
        @DisplayName("좋아요가 없는 질문 조회 시 likeCount=0 반환")  // 메서드: 상황→결과
        void serve_returns_likeCount_0_when_no_likes() { }
    }
}
```

- **클래스/메서드 `@DisplayName`은 한국어**, **메서드 식별자는 영어 스네이크 케이스**가 이 프로젝트의 표준입니다.
- 시나리오가 여러 개면 `@Nested` 클래스로 그룹화하고, 각 그룹에도 `@DisplayName`을 답니다.

### 6.4 유틸리티 메서드 명명

```
createSave()                          // 기본값 생성
createSave_With_{설명}(...)           // 특정 파라미터 지정 생성
createSave_{상태}()                   // 특정 상태 생성 (예: createSave_Anonymous, createSave_Expired)

예시:
createSave_With_Locale(String)
createSave_With_Date(Member, QuestionCycle, Question, LocalDate)
createSave_Anonymous()
createSave_Disabled(Member, String, String)
```

---

## 7. 새 도메인/테스트 추가 가이드

기존 인프라(`IntegrateTest`, `IntegrateTestConfig`)가 이미 갖춰져 있으므로, 새 기능 테스트는 보통 아래 흐름만 따르면 됩니다.

### 7.1 새 엔티티가 생겼다면 → TestUtils 추가

`integrate/test_config/utils/` 에 `TestXxxUtils`를 만들고 `@Component @RequiredArgsConstructor`로 Repository를 주입합니다.

```java
@Component
@RequiredArgsConstructor
public class TestXxxUtils {
    private final XxxRepository xxxRepository;
    private static int uniqueId = 0;   // 유니크 값이 필요할 때

    public Xxx createSave(/* 필요한 연관 엔티티 */) {
        Xxx xxx = Xxx.create(/* ... */);
        return xxxRepository.save(xxx);
    }
}
```

그리고 `IntegrateTest`에 주입 필드를 추가합니다.

```java
@Autowired protected TestXxxUtils testXxxUtils;
@Autowired protected XxxRepository xxxRepository;   // DB 검증이 필요하면
```

> tearDown은 `getMetamodel()`로 모든 엔티티를 자동 순회하므로 **별도 수정이 필요 없습니다.**

### 7.2 새 API URL 상수 추가 (필요 시)

`IntegrateTest`의 URL 상수 블록에 `static final`로 추가합니다.

```java
protected static final String XXX_API = API_V1 + "/xxx";
```

### 7.3 통합 테스트 클래스 작성

`integrate/{도메인}/` 아래에 `{기능}IntegrateTest`를 만들고 `IntegrateTest`를 상속합니다.

```java
@DisplayName("Xxx 기능 통합 테스트")
class XxxFeatureIntegrateTest extends IntegrateTest {

    private Member member;
    private String token;

    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        token = testAuthUtils.createBearerToken(member);
        // 필요한 사전 데이터 생성
    }

    @Test
    @DisplayName("정상 요청 시 200 OK")
    void xxx_with_valid_request_then_return_200_ok() throws Exception {
        mockMvc.perform(get(XXX_API)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.someField").exists());
    }
}
```

### 7.4 외부 의존을 새로 Mock 해야 한다면

1. `IntegrateTestConfig`에 `@Bean @Primary` Mock 등록
2. `IntegrateTest`에 `@Autowired protected`로 필드 추가
3. (상태 누수 우려 시) `resetMocks()`의 `Mockito.reset(...)` 인자에 추가
4. 테스트에서 `given(mock.method(...)).willReturn(...)`으로 stub

### 7.5 체크리스트

- [ ] 새 엔티티에 대한 `TestXxxUtils` 작성 (`@Component`, `test_config/utils`)
- [ ] `IntegrateTest`에 필요한 Repository / TestUtils / URL 상수 추가
- [ ] 통합 테스트는 `integrate/{도메인}/` 아래, `IntegrateTest` 상속
- [ ] 클래스/메서드 `@DisplayName`(한국어) + 메서드명(영어 스네이크) 규칙 준수
- [ ] 시나리오가 여러 개면 `@Nested`로 그룹화
- [ ] 응답 검증은 `jsonPath("$.필드")`, DB 검증은 Repository + AssertJ `.as(...)`
- [ ] 외부 의존이 있으면 Mock stub(`given().willReturn()`)으로 제어
- [ ] 여러 테스트 연속 실행 시 격리가 유지되는지 확인

---

## 부록: DB별 외래 키 제약 비활성화 문법

본 프로젝트 테스트는 **H2(`MODE=MySQL`)** 를 사용합니다. tearDown의 native query는 H2 문법입니다.

```java
// H2 (현재 사용)
entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();

// MySQL
entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

// PostgreSQL
entityManager.createNativeQuery("SET session_replication_role = replica").executeUpdate();
entityManager.createNativeQuery("SET session_replication_role = DEFAULT").executeUpdate();
```

---

## 부록: 환경 설정 / 의존성

### application-test.properties (`src/test/resources/`)

```properties
# Datasource (H2 인메모리, MySQL 호환 모드)
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect

# JWT (테스트 고정값)
jwt.secret-key=...
jwt.access-expire-time=3600000
jwt.refresh-expire-time=604800000

# OAuth (테스트 고정값)
google.oauth.web-client-id=test-google-client-id
apple.oauth.client-id=test-apple-client-id

# Firebase (테스트에서는 Mock)
firebase.service-account-json=
```

### build.gradle 테스트 관련

```gradle
// Spring Boot 3.4.1 / Java 21
testImplementation 'org.springframework.boot:spring-boot-starter-test'   // JUnit5, Mockito, AssertJ 포함
testImplementation 'org.springframework.security:spring-security-test'
testImplementation 'net.javacrumbs.shedlock:shedlock-provider-inmemory:7.7.0'  // 스케줄러 락 테스트용
testRuntimeOnly  'org.junit.platform:junit-platform-launcher'
runtimeOnly      'com.h2database:h2'                                       // H2 (런타임/테스트)
```

> Mockito·AssertJ는 `spring-boot-starter-test`에 포함되어 별도 의존성 추가가 필요 없습니다. ArchUnit은 현재 사용하지 않습니다.

---

## 요약

이 프로젝트 통합 테스트 패턴의 핵심:

1. **`IntegrateTest` 추상 부모 클래스**: 공통 도구·Repository·TestUtils·Mock·URL 상수를 상속으로 제공
2. **`@AfterEach tearDown`**: 메타모델 순회 + H2 `REFERENTIAL_INTEGRITY` 토글로 매 테스트 후 전체 DB 초기화 → 완벽한 격리
3. **`@BeforeEach` 초기화**: Mock 검증기 + ShedLock 락 상태 reset
4. **`test_config/utils`의 `TestXxxUtils`**: 데이터 생성 로직 재사용
5. **`IntegrateTestConfig`**: OAuth/Firebase 의존을 `@Primary` Mock으로 대체
6. **`@Nested` + 한국어 `@DisplayName` + 영어 스네이크 메서드명**: 시나리오 그룹화와 가독성
7. **응답은 `jsonPath("$.필드")`로 직접 검증** (공통 래퍼 없이 `ResponseEntity<DTO>` 반환)
8. **외부 의존은 `given().willReturn()`(BDDMockito)으로 stub**
