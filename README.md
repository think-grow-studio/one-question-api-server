# One-Question API Server

하루에 질문 하나(One-Question) 서비스의 백엔드 API 서버.
사용자에게 매일 질문 하나를 제공하고, 답변 기록·공개 피드·좋아요·리마인드 알림을 지원한다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.4.x (Web MVC, Data JPA, Security, Validation, Thymeleaf) |
| DB | Oracle Autonomous Database (prod/dev, wallet 접속) / H2 (local) |
| 인증 | JWT (jjwt) + OAuth (Google, Apple, Firebase 익명) |
| 푸시 알림 | Firebase Admin SDK (FCM) |
| 스케줄러 | Spring Scheduler + ShedLock (다중 인스턴스 중복 실행 방지) |
| 문서화 | springdoc-openapi (Swagger UI) |
| 모니터링 | Actuator + Prometheus (관리 포트 8081, 내부 전용) |
| 로깅 | Logback + MDC, 에러는 Discord Webhook Appender로 알림 |
| 배포 | Docker → GHCR → GitHub Actions로 VM 배포, nginx 리버스 프록시(TLS 종료) |

## 아키텍처 개요

바운디드 컨텍스트별 패키지 분리 + 컨텍스트 내부는 레이어드(클린 아키텍처 유사) 구조.

```
[Client (iOS App)]
      │ HTTPS
      ▼
[nginx] ─ TLS 종료, 리버스 프록시
      │ HTTP :8080
      ▼
[Spring Boot]
  presentation → application → domain ← infrastructure
      │                                      │
      ▼                                      ▼
[Oracle ADB]                    [FCM / Google·Apple OAuth / Discord]

관리 트래픽: Actuator :8081 (Prometheus scrape, 외부 비공개)
```

### 레이어 규칙 (컨텍스트 내부)

- **presentation**: `*Api`(Swagger 인터페이스) + `*Controller`, `request/`·`response/` DTO (record 사용)
- **application**: 유스케이스 오케스트레이션 (`*Application`). 트랜잭션 경계, 여러 도메인 서비스 조합
- **domain**: 엔티티, `*Repository`(Spring Data JPA 인터페이스), `*Service`(도메인 로직), `exception/`
- **infrastructure**: 외부 시스템 연동 (OAuth 클라이언트, FCM 게이트웨이, 스케줄러 등)

의존 방향은 presentation → application → domain 단방향. infrastructure는 domain을 참조하되 그 역은 없다.

## 패키지 구조

```
site.one_question
├── OneQuestionApplication.java
├── api/                          # 앱(모바일)용 REST API — 바운디드 컨텍스트별
│   ├── auth/                     # 로그인·토큰 재발급, Google/Apple/Firebase 검증, RefreshToken
│   ├── member/                   # 회원 프로필, 상태(MemberStatus), 소셜 연동(AuthSocialProvider)
│   ├── question/                 # 개인 데일리 질문: 질문 서빙, 사이클, 답변, 좋아요, 히스토리
│   ├── publicquestion/           # 공개 데일리 질문: 하루 1개 공용 질문, 익명 답변·좋아요
│   ├── answerpost/               # 답변 공개(피드) 게시, 피드 조회, 좋아요
│   ├── analysisreport/           # AI 분석 리포트 생성 요청, 리포트 소스 스냅샷
│   ├── backgroundjob/            # SQS 발행 대기용 백그라운드 작업
│   ├── notification/             # FCM 토큰 등록, 질문 리마인드 설정, 리마인드 스케줄러
│   ├── app_version/              # 앱 최소/최신 버전, 서버 라이브 여부 (강제 업데이트 판단용)
│   └── health/                   # 헬스체크
├── web/
│   ├── admin/                    # 관리자 웹 (Thymeleaf): 대시보드, 공개질문·답변포스트 관리
│   └── security/                 # 관리자용 JWT 쿠키 필터, AdminSecurityConfig
├── security/                     # 앱 API용 SecurityConfig, JwtValidationFilter, JwtService
│                                 # ActuatorSecurityConfig (8081 관리 포트 전용 체인)
├── config/                       # Firebase, OAuth, Swagger, Scheduler(ShedLock), JPA Auditing, Message
├── exception/                    # BaseException, GlobalExceptionHandler, ExceptionSpec 체계
├── filter/                       # MDC 로깅 필터 (requestId, memberId)
├── i18n/                         # LocaleNormalizer (Accept-Language 정규화)
├── logging/                      # Discord Webhook Appender (+ JWT 만료 로그 필터링)
└── common/domain/                # BaseEntity (createdAt/updatedAt 등 JPA Auditing)
```

## 도메인 모델 요약

- **Member** — 회원. 소셜 계정(Google/Apple/익명) 연동 상태, 권한, 상태(활성/탈퇴) 보유
- **Question / DailyQuestion / QuestionCycle** — 질문 풀에서 회원별로 매일 질문을 서빙.
  사이클 단위로 질문 순환, 후보(`DailyQuestionCandidate`)에서 선택·리로드(횟수 제한) 지원
- **DailyQuestionAnswer** — 개인 질문에 대한 답변 (회원당 질문당 1개)
- **PublicDailyQuestion / PublicDailyQuestionAnswer** — 전체 공용 "오늘의 질문"과 익명 답변.
  익명 닉네임(`AnonymousNickname`), 좋아요, 스케줄러(`PublicDailyQuestionProvisionScheduler`)가 매일 프로비저닝
- **AnswerPost** — 개인 답변을 공개 피드에 게시한 것. 좋아요(`AnswerPostLike`) 지원
- **BackgroundJob / AnalysisReport / AnalysisReportSource** — AI 분석 리포트 비동기 처리 요청.
  API 요청 시 본인의 개인 답변 10~15개를 검증하고 `PENDING` 작업, 리포트, 소스 스냅샷을 생성
- **FcmToken / QuestionReminderSetting** — 기기별 푸시 토큰과 회원별 리마인드 시간 설정.
  `QuestionRemindScheduler`가 설정 시간에 맞춰 FCM 발송 (ShedLock으로 단일 실행 보장)
- **RefreshToken** — 리프레시 토큰 저장·회전(rotation) 검증

## 공통 컨벤션

- DTO는 **record**, 의존성 주입은 `@RequiredArgsConstructor`
- 엔티티 생성은 **정적 팩토리 메서드** (`create()`)
- 예외는 컨텍스트별 `*Exception` + `*ExceptionSpec`(에러코드/메시지 스펙) 상속 구조,
  `GlobalExceptionHandler`에서 `ExceptionResponse`로 일괄 변환, 메시지는 i18n(`messages*.properties`)
- 인증된 사용자 ID는 `@PrincipalId` 커스텀 어노테이션으로 주입
- 날짜 경계는 클라이언트 타임존 헤더 기반 (`DatePolicy` 참고)
- 요청 로그는 MDC(requestId, memberId) 기반, ERROR 레벨은 Discord로 전송

## 프로필과 인프라

| 프로필 | DB | 용도 |
|---|---|---|
| `local` | H2 (in-memory) | 로컬 개발 |
| `dev` | Oracle ADB (dev wallet) | 개발 서버 |
| `prod` | Oracle ADB (main wallet), `ddl-auto=validate` | 운영. 시크릿은 전부 환경변수 |

- **배포 파이프라인**: `.github/workflows/` — Gradle 빌드 → Docker 이미지 빌드 → GHCR push → VM에 compose 배포 (`docker-compose.prod.yml` / `docker-compose.dev.yml`)
- **nginx**: `nginx/prod.conf`, `nginx/dev.conf`. conf는 디렉토리 단위 bind mount (inode 고정 이슈 회피 — `docs/nginx-bind-mount-inode-postmortem.md`)
- **Actuator**: 8081 포트 분리, `prometheus`·`health`만 노출, 별도 SecurityFilterChain
- **DB 스키마 변경**: `src/main/resources/migration/`의 SQL을 수동 적용 (prod는 validate만 수행)

## 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'   # 로컬 (H2)
./gradlew test                                              # 테스트
./gradlew build                                             # 빌드
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## 추가 문서

- `src/test/java/site/one_question/CLAUDE.md` — 테스트 작성 패턴·규칙
- `JPA_INTERNAL_BEHAVIOR.md` — JPA 동작 관련 정리
- `DEADLOCK_REPORT.md`, `docs/` — 운영 장애 포스트모템 모음
- `PR_GUIDE.md` — PR 작성 가이드
