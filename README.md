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
| 메시징 | AWS SQS (AI 분석 리포트 백그라운드 작업 발행) |
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
│   ├── backgroundjob/            # 외부 큐 발행 작업, 공통 Publisher Scheduler와 claim 복구
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
  API 요청 시 본인의 개인 답변 10~15개를 검증하고 `PENDING` 작업, 리포트, 소스 스냅샷을 생성.
  공통 5초 Publisher Scheduler가 작업을 CAS claim한 뒤 타입별 Publisher로 외부 큐에 발행
- **FcmToken / QuestionReminderSetting** — 기기별 푸시 토큰과 회원별 리마인드 시간 설정.
  `QuestionRemindScheduler`가 설정 시간에 맞춰 FCM 발송 (ShedLock으로 단일 실행 보장)
- **RefreshToken** — 리프레시 토큰 저장·회전(rotation) 검증

### BackgroundJob Worker 수신 계약

SQS 발행은 DB 트랜잭션 밖에서 실행되므로 메시지가 보이는 시점에 DB 작업은 아직
`PUBLISHING`일 수 있다. Worker는 메시지의 `jobId`로 DB 상태를 다시 확인하고 다음
계약을 지킨다.

- `PENDING` 또는 `PUBLISHING`: 조기 수신이다. 업무 처리를 시작하지 않고 메시지를
  acknowledge/delete하지 않는다. visibility timeout 이후 재전달되게 둔다.
- `QUEUED`: `QUEUED → PROCESSING` CAS에 성공한 Worker만 업무 처리를 시작한다.
- `PROCESSING`, `SUCCEEDED`, `FAILED`: DB에 이미 처리 소유권 또는 종결 결정이
  durable하게 기록된 중복 수신이므로 업무를 반복하지 않고 acknowledge/delete할 수 있다.
- `QUEUED → PROCESSING`을 선점한 Worker도 처리 결과를 DB에 durable하게 반영한 뒤에만
  acknowledge/delete한다. 결과를 확정하지 못한 재시도 가능 오류에서는 삭제하지 않는다.

즉 Worker는 단순히 메시지를 받았다는 이유로 삭제하지 않으며, durable한 종결 또는
이미 처리 중이라는 DB 결정을 확인한 뒤에만 삭제한다.

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
- **이미지 태그**: 배포는 `latest`가 아니라 커밋 단위 불변 태그(`sha-xxxxxxx`)로 고정한다. 빌드가 태그를 `image-url.txt`로 내려보내고, 배포 워크플로가 이를 `IMAGE_TAG`로 VM의 `/home/ubuntu/.env`에 기록한다. **prod는 MAIN VM이 쓴 태그를 SUB VM이 job output으로 그대로 물려받는다** — `latest`를 쓰면 MAIN 배포 후 새 빌드가 올라올 때 SUB만 다른 버전을 pull 해서, nginx가 두 VM에 라운드로빈 하는 동안 버전이 섞인 채 서비스된다. 배포 워크플로는 태그가 `sha-`로 시작하지 않으면 중단한다
  - 롤백: VM의 `/home/ubuntu/.env`에서 `IMAGE_TAG`를 이전 값으로 바꾸고 `docker compose -f <파일> up -d`
  - ⚠️ 두 빌드 워크플로는 `workflow_run`으로 트리거되는데, 이 이벤트의 `GITHUB_SHA`는 **기본 브랜치의 마지막 커밋**이다. 따라서 `metadata-action`의 `type=sha`를 쓰면 실제 빌드한 커밋이 아닌 값이 태그로 붙는다. `github.event.workflow_run.head_sha`에서 직접 계산할 것
- **Oracle wallet**: 이미지에 굽지 않는다. wallet 폴더를 `tar.gz` + base64 한 값을 `ORACLE_WALLET_BASE64` 환경변수로 넘기면 `docker-entrypoint.sh`가 컨테이너 기동 시 `/app/src/main/resources/` 아래로 풀어서 쓴다 (빌드 타임에 풀면 시크릿 마운트를 쓰더라도 복원 결과물이 이미지 레이어에 남는다). **푸는 경로는 `ORACLE_DB_URL`의 `TNS_ADMIN=./src/main/resources/<wallet>` 과 맞물려 있어, 한쪽만 바꾸면 기동에 실패한다**
- **Graceful shutdown**: `application.properties`의 종료 예산과 compose의 `stop_grace_period`는 **한 세트로 읽어야 한다.** 웹 드레이닝 25s + 스케줄러 대기 20s = 최대 45s이므로 `stop_grace_period`(60s)가 그보다 짧으면 Docker가 먼저 SIGKILL을 보내 드레이닝이 잘린다. 한쪽만 조정하지 말 것
  - `server.shutdown=graceful`은 Boot 3.4 기본값이지만 위 계산의 전제라 명시해 뒀다
  - `spring.task.scheduling.shutdown.await-termination`은 기본값이 `false`라 반드시 켜야 한다. BackgroundJob 발행 배치가 SQS 호출 도중 끊기면 그 작업은 `PUBLISHING`으로 남아 publish claim이 만료될 때까지 아무도 집어가지 못한다
  - 컨테이너의 PID 1은 `docker-entrypoint.sh`가 아니라 **java여야 한다** (스크립트가 `exec "$@"`로 자신을 교체). PID 1이 셸이면 SIGTERM이 JVM에 전달되지 않아 위 설정이 전부 무의미해진다
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
- `PR_GUIDE.md` — PR 작성 가이드

기술 정리와 운영 포스트모템은 `docs/` 아래에 모은다.

- `docs/jpa-internal-behavior.md` — JPA 내부 동작 정리
- `docs/jpa-bulk-modifying-and-persistence-context.md` — bulk `@Modifying` JPQL과 영속성 컨텍스트
- `docs/optimistic-lock-and-ddd.md` — 낙관적 락과 DDD: 동시 상태 전이 설계 정리 (analysisreport 워커 설계 시 참고)
- `docs/version-cas-compare.md` — 낙관적 락(version) vs 상태 CAS 비교
- `docs/docker-network-iptables.md` — Docker 컨테이너 네트워크와 iptables
- `docs/ora-12860-deadlock-postmortem.md` — ORA-12860 데드락 장애 분석
- `docs/json-compression-postmortem.md` — HTTP JSON 압축 적용 기록
- `docs/nginx-bind-mount-inode-postmortem.md` — nginx conf bind mount inode 고정 문제
- `docs/superpowers/` — 기능별 설계 스펙·구현 계획
