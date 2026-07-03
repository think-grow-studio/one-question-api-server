# 테스트 작성 가이드

통합 테스트 중심 전략: MockMvc로 실제 HTTP 요청 → 실제 H2 DB 커밋 결과 검증. 인프라 코드의 상세는 `integrate/test_config/IntegrateTest.java`와 `IntegrateTestConfig.java`를 직접 읽을 것 — 이 문서는 규칙만 정리한다.

## 구조 규칙

- 통합 테스트는 `integrate/{도메인}/` 아래, 반드시 `IntegrateTest`(추상 부모)를 상속
- Spring Context가 필요 없는 순수 단위 테스트는 `domain/`, 글로벌 설정 테스트는 `global/`
- 테스트 데이터 생성은 `integrate/test_config/utils/`의 `TestXxxUtils` 컴포넌트로만 (테스트 안에서 엔티티 직접 조립 금지)

## IntegrateTest가 제공하는 것 (재선언 금지)

- `mockMvc`, `objectMapper`, 전 도메인 Repository, 전 `TestXxxUtils`, `transactionTemplate`
- API URL 상수 (`QUESTIONS_API`, `AUTH_API` 등 — 새 API는 여기에 `static final`로 추가)
- OAuth/Firebase 검증기는 `IntegrateTestConfig`에서 `@Primary` Mock으로 대체됨 → 테스트에서 `given(...).willReturn(...)`(BDDMockito)으로 stub
- `@BeforeEach`: Mock reset + ShedLock 인메모리 락 reset (스케줄러 테스트 간 락 누수 방지)
- `@AfterEach tearDown`: 메타모델 순회로 **전체 테이블 삭제** → 새 엔티티를 추가해도 tearDown 수정 불필요. `@Transactional` 롤백 방식을 쓰지 않는 이유는 MockMvc가 실제 커밋한 데이터까지 검증하기 위함이므로 바꾸지 말 것
- tearDown의 `SET REFERENTIAL_INTEGRITY FALSE/TRUE`는 **H2 전용 문법** (테스트 DB는 H2 `MODE=MySQL`, `src/test/resources/application-test.properties`)

## 작성 컨벤션

- 클래스명: `{동작}{대상}IntegrateTest` (예: `ServeDailyQuestionIntegrateTest`)
- 메서드명: 영어 스네이크 케이스 `{동작}_{조건}_then_{결과}` / `@DisplayName`: 한국어 (클래스·`@Nested`·메서드 모두)
- 시나리오가 여러 개면 `@Nested` 클래스로 그룹화
- 응답 검증: 컨트롤러가 공통 래퍼 없이 `ResponseEntity<DTO>`를 반환하므로 `jsonPath("$.필드")`로 DTO 필드 직접 접근
- DB 검증: Repository 직접 조회 + AssertJ, 실패 메시지는 `.as("...")` 한국어로
- 인증 헤더: `testAuthUtils.createBearerToken(member)` → `.header(HttpHeaders.AUTHORIZATION, token)`, 타임존은 `HttpHeaderConstant.TIMEZONE` 헤더
- 다양한 잘못된 입력 검증은 `@ParameterizedTest` + `@MethodSource`
- TestUtils 메서드 명명: `createSave()` / `createSave_With_{설명}(...)` / `createSave_{상태}()` (예: `createSave_Anonymous`, `createSave_Expired`)

## 새 도메인/기능 테스트 추가 절차

1. 새 엔티티가 있으면 `test_config/utils/`에 `TestXxxUtils` 작성 (`@Component @RequiredArgsConstructor`, 유니크 값은 `static int uniqueId` 패턴)
2. `IntegrateTest`에 `@Autowired protected` 필드 추가 (TestUtils + 검증용 Repository), 새 API URL 상수 추가
3. 새 외부 의존은 `IntegrateTestConfig`에 `@Bean @Primary` Mock 등록 + `IntegrateTest`의 `resetMocks()`에 포함
4. `integrate/{도메인}/`에 테스트 클래스 작성, 위 컨벤션 준수
