# Spring Boot API Template 설계

작성일: 2026-08-01

## 목적

One-Question의 기본 인프라를 다른 서비스에서도 시작점으로 쓸 수 있도록, 독립 실행 가능한
**GitHub Template 저장소**로 분리한다. 공통 인증 기반은 제공하되 특정 서비스 도메인이나
외부 로그인 제공자에는 결합하지 않는다.

템플릿은 Java 21, Spring Boot 3.4.1, PostgreSQL, Spring Web MVC, Spring Security,
Spring Data JPA, Validation, JJWT, Lombok을 기본으로 한다.

## 핵심 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 배포 형태 | 독립 Spring Boot GitHub Template 저장소 | 새 프로젝트가 코드를 직접 소유하고 자유롭게 변경할 수 있다. 공통 라이브러리의 확장 지점과 버전 결합을 피한다. |
| 구조 | 바운디드 컨텍스트별 패키지 + 컨텍스트 내부 레이어 | 기존 프로젝트의 일관된 의존 방향을 재사용한다. |
| 사용자 모델 | 최소 `Account` 제공 | 인증 기반을 바로 실행할 수 있으면서 서비스별 프로필 모델은 강제하지 않는다. |
| 최초 인증 | 외부 모듈이 신원을 검증한 뒤 `AuthTokenIssuer.issue(accountId)` 호출 | Google, Apple 등 특정 공급자와 템플릿을 분리한다. |
| Refresh Token | 사용자당 1개, DB에 원문 저장 | 현재 One-Question과 같은 단순한 발급·재발급·로그아웃 흐름을 유지한다. |
| 스키마 관리 | JPA 모델만 제공, migration SQL 제외 | 실제 스키마 관리는 템플릿을 사용하는 프로젝트가 결정한다. 애플리케이션은 `ddl-auto=validate`를 사용한다. |
| 예외 메시지 | 예외 스펙에 기본 클라이언트 메시지 보유 | i18n과 `MessageResolver` 없이 일관된 오류 응답을 제공한다. |

## 패키지 구조

기본 패키지명은 템플릿 생성 시 변경할 수 있는 예시 이름을 사용한다.

```text
com.example.template
├── ApiServerApplication.java
├── api/
│   ├── account/
│   │   ├── application/
│   │   └── domain/
│   │       ├── Account.java
│   │       ├── AccountRepository.java
│   │       ├── AccountRole.java
│   │       └── AccountStatus.java
│   └── auth/
│       ├── presentation/
│       │   ├── AuthController.java
│       │   ├── request/
│       │   └── response/
│       ├── application/
│       │   ├── AuthApplication.java
│       │   └── AuthTokenIssuer.java
│       ├── domain/
│       │   ├── RefreshToken.java
│       │   ├── RefreshTokenRepository.java
│       │   └── RefreshTokenService.java
│       └── exception/
├── security/
│   ├── SecurityConfig.java
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── AuthenticatedPrincipal.java
│   ├── AuthenticationEntryPointImpl.java
│   └── AccessDeniedHandlerImpl.java
├── common/
│   ├── domain/BaseEntity.java
│   ├── annotation/PrincipalId.java
│   └── filter/MdcLoggingFilter.java
├── exception/
│   ├── BaseException.java
│   ├── ExceptionResponse.java
│   ├── GlobalExceptionHandler.java
│   └── spec/
└── config/
    └── JpaAuditConfig.java
```

컨텍스트 내부 의존 방향은 `presentation → application → domain`이다. `security`, `common`,
`exception`, `config`는 여러 컨텍스트에서 사용하는 기반 코드만 둔다. DTO는 `record`, 엔티티는
정적 팩토리 `create()`, 생성자 주입은 `@RequiredArgsConstructor`를 사용한다.

## 도메인 모델

### Account

`Account`는 인증에 필요한 최소 정보만 가진다.

```text
Account
├── id
├── email
├── role
├── status
├── createdAt
├── createdBy
├── updatedAt
└── updatedBy
```

`role`과 `status`는 enum으로 두며, 최소한 일반 사용자 권한과 활성 상태를 표현한다. 서비스별
닉네임, 프로필, 약관 동의, 소셜 공급자 정보는 포함하지 않는다.

### BaseEntity와 감사 주체

`BaseEntity`는 다음 JPA Auditing 필드를 유지한다.

```text
createdAt
createdBy   // Account ID
updatedAt
updatedBy   // Account ID
```

`JpaAuditConfig`의 `AuditorAware<Long>`는 현재 `AuthenticatedPrincipal`에서 Account ID를
읽는다. 인증되지 않은 최초 처리, 배치, 시스템 작업도 가능하므로 `createdBy`와 `updatedBy`는
nullable로 둔다.

### RefreshToken

현재 One-Question의 엔티티 구조와 동작을 기준으로 한다.

```text
RefreshToken
├── id
├── account       // LAZY 연관
├── token         // JWT 원문, VARCHAR(512)
├── expiredAt
├── createdAt
├── createdBy
├── updatedAt
└── updatedBy
```

사용자당 활성 Refresh Token은 하나다. `account_id`는 `NOT NULL UNIQUE`, `token`은
`VARCHAR(512) NOT NULL`, `expired_at`은 `NOT NULL`이어야 한다. 저장 시 기존 행이 있으면
`token`과 `expiredAt`을 갱신하고, 없으면 새 행을 생성한다. 동시에 최초 발급되어 유니크
제약 충돌이 발생하면 기존 행을 다시 조회해 갱신한다. 별도 토큰 해시나 이력은 저장하지 않는다.

## 인증 및 데이터 흐름

### 최초 발급

템플릿은 로그인 공급자를 제공하지 않는다. 새 프로젝트의 외부 인증 모듈이 공급자 응답을
검증하고 `Account`를 결정한 뒤 아래 애플리케이션 포트를 호출한다.

```java
AuthTokens issue(Long accountId);
```

발급 흐름은 다음과 같다.

1. `accountId`로 활성 `Account`를 조회한다.
2. Access Token과 Refresh Token을 발급한다.
3. 해당 Account의 Refresh Token 행을 신규 저장하거나 원문 토큰과 만료 시각으로 갱신한다.
4. 두 토큰을 호출자에게 반환한다.

새로 발급하면 같은 사용자의 기존 Refresh Token은 더 이상 DB 값과 일치하지 않아 무효가 된다.

### 재발급

공개 API는 `POST /api/v1/auth/reissue`이다. 요청 record는 `refreshToken` 필드 하나를 가지며,
JSON 계약은 `{ "refreshToken": "..." }`이다. 다음 순서로 재발급한다.

1. JWT 서명, 만료, Refresh Token 타입을 검증하고 Account ID를 읽는다.
2. Account ID로 저장된 Refresh Token을 조회한다.
3. 저장된 원문, 요청 원문, DB 만료 시각을 검증한다.
4. Access Token과 Refresh Token을 새로 발급한다.
5. 기존 행의 원문 토큰과 만료 시각을 새 값으로 갱신한다.

응답 record는 `accessToken`, `refreshToken` 두 필드만 제공한다.

### API 인증

Access Token은 기본 30분, Refresh Token은 기본 7일이며 모두 설정으로 변경할 수 있다.
`JwtAuthenticationFilter`는 `Authorization: Bearer <token>`을 읽고, 유효한
**Access Token 타입만** SecurityContext 인증으로 등록한다. Refresh Token을 Bearer 인증에
사용하는 것은 거부한다.

### 로그아웃

`POST /api/v1/auth/logout`은 Access Token 인증이 필요하다. 인증된 Account ID로 저장된
Refresh Token 행을 삭제한다. 이미 발급된 Access Token은 30분 만료 전까지 유효하며,
Access Token denylist는 제공하지 않는다.

## 오류 처리

공통 오류 응답은 아래 정보를 가진다.

```text
requestId
status
code
message
```

- `ExceptionSpec`은 HTTP 상태, 안정적인 에러 코드, 기본 클라이언트 메시지를 제공한다.
- 컨텍스트 예외는 `BaseException`을 상속하고 해당 컨텍스트의 `*ExceptionSpec`을 사용한다.
- `AuthExceptionSpec`은 잘못된 JWT, 잘못된 토큰 타입, Refresh Token 만료, 미존재,
  원문 불일치를 구분한다.
- `GlobalExceptionHandler`는 도메인 예외, 요청 검증 실패, JSON 파싱 오류, 알 수 없는 오류를
  `ExceptionResponse`로 변환한다.
- 미인증 401은 `AuthenticationEntryPointImpl`, 인가 실패 403은 `AccessDeniedHandlerImpl`을
  통해 같은 응답 형태를 사용한다.
- i18n은 사용하지 않으며 로그용 상세 정보와 클라이언트 메시지를 분리한다.

## 설정

- Java 21 / Spring Boot 3.4.1
- Spring Web MVC, Security, Validation, Data JPA
- PostgreSQL JDBC 드라이버
- JJWT, Lombok
- `spring.jpa.hibernate.ddl-auto=validate`
- Access Token 만료 기본값: 30분
- Refresh Token 만료 기본값: 7일
- JWT issuer, audience, secret과 DB 접속 정보는 환경변수로만 주입
- 저장소에 동작 가능한 기본 secret이나 실제 DB 자격 증명을 커밋하지 않음
- Security 공개 경로는 `POST /api/v1/auth/reissue`와 프레임워크 오류 처리 경로만 허용하고,
  `POST /api/v1/auth/logout`을 포함한 나머지 API는 인증을 요구

PostgreSQL 테이블과 제약은 템플릿 사용 프로젝트가 마련해야 한다. migration SQL을 제공하지
않으므로 README에 `ddl-auto=validate` 실행 전 스키마가 필요하다는 점과 필요한 엔티티 구조를
명시해야 한다.

## 명시적 비범위

다음 항목은 템플릿에 포함하지 않는다.

- Google, Apple 등 OAuth 공급자 구현과 SDK
- Firebase 인증 및 Firebase Admin SDK
- 이메일/비밀번호 회원가입과 로그인
- i18n, 메시지 번들, `MessageResolver`
- 관리자 `web/`, Thymeleaf
- migration SQL과 자동 migration 도구
- 테스트 코드
- 서비스별 도메인 모델과 One-Question 전용 경로
- Refresh Token 해시 저장
- `familyId`, 토큰 이력, 재사용 탐지
- 기기별 세션과 다중 로그인 관리
- Access Token denylist와 즉시 폐기
- 공통 인증 라이브러리 배포 및 기존 프로젝트로의 자동 업데이트 전파

## 구현 시 주의점

1. `AuthTokenIssuer`는 외부 공급자 검증을 하지 않는다. 호출 전에 신원 검증과 Account 결정이
   끝났다는 애플리케이션 경계를 문서화한다.
2. Access/Refresh 토큰에 타입 claim을 넣고, 발급·재발급·필터의 각 경계에서 기대 타입을
   명시적으로 확인한다.
3. `AccountStatus`가 비활성이면 최초 발급과 재발급을 모두 거부한다.
4. Refresh Token은 원문 저장이므로 DB 읽기 권한과 로그 노출을 최소화한다. 요청/응답 본문,
   예외 상세 정보, MDC에 토큰을 기록하지 않는다.
5. 사용자당 Refresh Token 하나라는 불변식은 저장 로직과 운영 스키마가 함께 보장한다.
   `account_id` 유니크 제약을 두고, 동시 최초 발급의 유니크 충돌은 기존 행 재조회 후 갱신으로
   처리한다.
6. `createdBy`와 `updatedBy`는 인증 없는 흐름에서 null일 수 있다. Account 최초 생성이나
   시스템 작업이 감사 주체를 억지로 가장하지 않도록 한다.
7. `ddl-auto=validate`는 스키마를 만들지 않는다. 템플릿 생성 직후 실행 방법에는 사용자가
   PostgreSQL 스키마를 준비해야 한다는 안내가 반드시 포함되어야 한다.
8. 템플릿에서 패키지명을 바꿀 때 Security 컴포넌트 스캔, JPA 엔티티 스캔, 설정 프로퍼티
   prefix가 함께 바뀌는지 확인한다.

## 완료 기준

템플릿 저장소는 PostgreSQL 스키마와 필수 환경변수가 준비된 상태에서 부팅할 수 있어야 한다.
외부 인증 구현은 Account ID를 결정한 뒤 `AuthTokenIssuer`만 호출해 토큰을 발급할 수 있어야
하며, 제공 REST API는 재발급과 로그아웃으로 한정한다.
