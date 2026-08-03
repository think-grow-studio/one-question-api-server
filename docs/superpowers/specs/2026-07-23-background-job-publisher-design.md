# BackgroundJob 공통 큐 발행 설계

## 배경

현재 AI 분석 리포트는 `AnalysisReportJobPublishScheduler`가 전용으로
`BackgroundJobType.ANALYSIS_REPORT` 작업을 조회하고 SQS에 발행한다. 이 구조는 새로운
작업 타입이 추가될 때마다 도메인별 스케줄러와 공통 발행 정책을 복제하게 된다.

향후에는 다음 두 종류의 워커를 운영할 예정이다.

- AI 분석 리포트: Python 워커
- 예약 결제 등 Spring 도메인 작업: 현재 API와 같은 Spring 코드베이스의 워커

두 워커 모두 SQS를 경계로 사용한다. Spring API와 Spring 워커는 초기에는 같은
프로세스에서 실행할 수 있지만, 설정으로 역할을 분리해 동일한 애플리케이션을 별도
인스턴스로 배포할 수 있어야 한다.

## 목표

- `BackgroundJob`을 외부 큐에 발행할 작업을 나타내는 도메인으로 한정한다.
- 큐 발행 스케줄링, 대상 조회, 발행 상태 전이, 발행 재시도를 `backgroundjob`
  컨텍스트에서 공통 관리한다.
- 작업별 메시지 조립과 목적지 큐 선택은 `BackgroundJobType`별 Publisher가 담당한다.
- JPQL CAS로 작업을 원자적으로 선점하고 lease 만료 작업을 복구한다.
- SQS 외부 호출은 DB 트랜잭션 밖에서 실행한다.
- AI 분석 리포트 전용 스케줄러를 제거한다.
- 발행 스케줄러는 기본 5초 간격으로 실행하며 properties에서 변경할 수 있게 한다.
- local/test에서는 실제 발행 스케줄러가 실행되지 않게 한다.

## 비목표

- Python AI 워커 구현
- Spring 예약 결제 워커 구현
- Worker의 업무 처리 재시도 정책 통합
- 모든 작업 타입을 하나의 물리 SQS 큐에 합치기
- exactly-once 전달 보장

## 책임 경계

공통 발행 계층은 SQS 등 외부 큐에 메시지를 넣을 때까지만 책임진다.

```text
업무 트랜잭션
  → BackgroundJob(PENDING) 저장
  → 공통 BackgroundJob Publisher Scheduler
  → BackgroundJob(PUBLISHING) 선점
  → JobType별 Publisher
  → 목적지 큐 발행
  → BackgroundJob(QUEUED)
```

큐 발행 실패는 공통 발행 계층이 재시도한다. 큐 발행 이후 AI 분석 실패나 결제 실패는
각 Worker와 업무 도메인의 처리 정책이다.

SQS 중복 발행은 완전히 제거할 수 없으며 Worker가 `jobId`로 멱등 처리한다.
Publisher의 claim은 불필요한 동시 발행을 줄이고, 발행자가 중단됐을 때 작업이
`PUBLISHING`에 영구 고착되지 않게 복구하는 장치다.

## 구성요소

### backgroundjob 컨텍스트

```text
api/backgroundjob/
├── application/
│   ├── BackgroundJobPublishApplication
│   ├── BackgroundJobPublishProcessor
│   ├── PublishAttemptResult
│   └── PublishAttemptOutcome
├── domain/
│   ├── BackgroundJob
│   ├── BackgroundJobType
│   ├── BackgroundJobRepository
│   ├── BackgroundJobService
│   ├── ClaimedBackgroundJob
│   ├── BackgroundJobPublisher
│   └── BackgroundJobPublisherRegistry
└── infrastructure/
    └── BackgroundJobPublishScheduler
```

`BackgroundJobPublishScheduler`는 infrastructure에 위치한다. “backgroundjob으로 이동”은
스케줄러를 domain 레이어에 넣는다는 뜻이 아니라, `backgroundjob` 바운디드 컨텍스트가
스케줄링 책임을 소유한다는 뜻이다.

`BackgroundJobPublisher`는 외부 발행 전략을 추상화하는 domain port로서 다음 계약을
제공한다. 구현체는 이 계약에만 의존하고 공통 Application을 참조하지 않는다.

```java
public interface BackgroundJobPublisher {
    BackgroundJobType supportedType();
    void publish(ClaimedBackgroundJob job);
    default void onPublishExhausted(Long jobId, Exception cause) {}
}
```

- `supportedType()`: 담당하는 작업 타입
- `publish(job)`: 메시지 조립과 외부 큐 발행
- `onPublishExhausted(...)`: 공통 발행 재시도 소진 시 필요한 도메인 후처리

`ClaimedBackgroundJob`은 `id`, `jobType`, `jobData`, `traceId`, `claimId`,
`retryCount`만 담는 불변
snapshot이다. JPQL bulk update 후 분리된 JPA 엔티티를 외부 호출 구간으로 넘기지 않는다.

`BackgroundJobPublisherRegistry`는 Spring이 주입한 Publisher 목록을
`BackgroundJobType`으로 매핑한다. 같은 타입을 지원하는 구현체가 둘 이상이거나 enum에
대응하는 구현체가 없으면 시작 시 실패시킨다. Dispatcher에 작업 타입별 `switch`는 두지
않는다.

`BackgroundJobPublishApplication`은 타입별 발행 대상 ID를 조회하고
`BackgroundJobPublishProcessor`에 한 건씩 위임한다. Processor는 전파 속성이 명시된
두 종류의 `TransactionTemplate`을 사용한다.

- 전체 한 건 처리: `PROPAGATION_NOT_SUPPORTED`
- 선점, 성공 상태 반영, 실패 상태 반영: `PROPAGATION_REQUIRES_NEW`

따라서 호출자에게 활성 트랜잭션이 있더라도 한 건 처리 동안 이를 중단하고, DB 상태
변경에만 각각 독립된 짧은 트랜잭션을 연다. Publisher의 SQS 외부 호출은 두 DB
트랜잭션 사이의 비트랜잭션 구간에서 실행한다. 이는 `@Transactional(REQUIRES_NEW)`를
Publisher 전체에 적용하는 방식이 아니라, 프로그램 방식으로 필요한 DB 구간만
명시적으로 제어하는 것이다.

선점 트랜잭션은 CAS 성공 후 Job을 다시 조회해 `ClaimedBackgroundJob` snapshot으로
변환하고 트랜잭션 밖으로 반환한다.

Processor가 최종 발행 실패를 커밋하면
`PublishAttemptOutcome(PublishAttemptResult.EXHAUSTED, cause)`를 반환한다. Processor는
후처리를 직접 실행하지 않는다. Application은 상태 트랜잭션이 끝난 뒤 outcome의 원인과
함께 Publisher의 `onPublishExhausted(...)`를 호출한다.
후처리에 DB 변경이 필요하면 Publisher 구현체가 전파 속성을 명시한 자체
`TransactionTemplate`로 관리한다.

### analysisreport 컨텍스트

기존 `AnalysisReportJobPublishApplication`의 분석 리포트 전용 책임은 application의
`AnalysisReportJobPublisher`로 옮긴다. SQS 전송은 domain port와 infrastructure
구현으로 분리해 application이 infrastructure 구현을 직접 참조하지 않게 한다.

```text
api/analysisreport/
├── application/
│   └── AnalysisReportJobPublisher
├── domain/
│   └── AnalysisReportJobQueueGateway
└── infrastructure/
    └── SqsAnalysisReportJobQueueGateway
```

`AnalysisReportJobPublisher`는 다음을 담당한다.

1. 필요한 경우 짧은 읽기 트랜잭션으로 `AnalysisReport` 역조회
2. 읽기 트랜잭션 안에서 불변 메시지 DTO 생성
3. 읽기 트랜잭션 종료 후 분석 리포트 전용 SQS 큐로 발행
4. 발행 재시도 소진 시 별도 짧은 트랜잭션으로 `AnalysisReport.FAILED` 전이

분석 리포트 큐 URL과 메시지 계약은 계속 `analysisreport` 컨텍스트가 소유한다.

## 상태와 claim 데이터

`BackgroundJobStatus`는 다음 상태를 사용한다.

```java
public enum BackgroundJobStatus {
    PENDING,
    PUBLISHING,
    QUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED
}
```

- `PENDING`: 큐 발행 대기. `next_retry_at`이 미래이면 발행 재시도 대기
- `PUBLISHING`: Publisher가 선점해 외부 큐 발행 중
- `QUEUED`: SQS 발행 성공, Worker 처리 대기
- `PROCESSING`: Worker가 실제 업무 처리 중
- `SUCCEEDED`: Worker 업무 처리 성공
- `FAILED`: 발행 또는 처리 재시도 소진

`BackgroundJob`에 다음 필드를 추가한다.

```java
/**
 * 현재 SQS 발행 시도의 소유권 식별자.
 * lease 만료 후 과거 발행자가 새 발행자의 상태를 덮어쓰지 못하게 CAS 조건에 사용한다.
 */
@Column(name = "publish_claim_id", length = 36)
private String publishClaimId;

/**
 * SQS 발행 선점의 만료 시각.
 * 이 시각이 지나면 중단된 PUBLISHING 작업을 재시도 대상으로 복구한다.
 */
@Column(name = "publish_claim_until")
private Instant publishClaimUntil;
```

`publishClaimId`는 서버에 배포하는 공통 값이 아니다. 각 Job 발행 시도마다
`UUID.randomUUID()`로 새로 만들고, 선점 CAS에 성공한 실행만 해당 값을 소유한다.

DB 마이그레이션은 다음을 포함한다.

- `publish_claim_id VARCHAR2(36)` nullable
- `publish_claim_until TIMESTAMP WITH TIME ZONE` nullable
- 만료 claim 조회용 인덱스 `(status, publish_claim_until)`

이 기능은 아직 운영 배포 전이므로 별도의 `ENQUEUED → QUEUED` 데이터 변환이나
호환 배포 단계는 두지 않는다. 기존 `background_job` 생성 마이그레이션을 최종
스키마로 직접 수정하고, 애플리케이션 enum과 Worker 계약은 처음부터 `QUEUED`만
사용한다.

상태별 필드 규칙은 다음과 같다.

- `PUBLISHING`: `publish_claim_id`, `publish_claim_until` 모두 필수
- `PUBLISHING` 이외의 모든 상태: 두 claim 필드 모두 NULL

### Worker 수신과 acknowledge/delete 계약

SQS 호출과 `PUBLISHING → QUEUED` 상태 기록은 하나의 원자적 트랜잭션이 아니다. SQS
전송이 성공하면 Publisher의 완료 CAS가 커밋되기 전에도 Worker가 메시지를 받을 수
있다. Worker는 메시지 수신 자체를 처리 가능 또는 삭제 가능의 근거로 삼지 않고
`jobId`로 최신 DB 상태를 확인한다.

| 관찰한 상태 | Worker 동작 | acknowledge/delete |
|---|---|---|
| `PENDING`, `PUBLISHING` | 조기 수신으로 판단하고 업무를 시작하지 않는다 | 하지 않는다. visibility timeout 후 재전달 |
| `QUEUED` | `QUEUED → PROCESSING` CAS를 시도하고 성공자만 업무를 시작한다 | 처리 결과의 durable 기록 전에는 하지 않는다 |
| `PROCESSING` | 다른 수신이 이미 durable한 처리 소유권을 얻은 중복으로 판단한다 | 반복 처리하지 않고 가능 |
| `SUCCEEDED`, `FAILED` | 이미 durable하게 종결된 중복으로 판단한다 | 반복 처리하지 않고 가능 |

`QUEUED → PROCESSING`을 선점한 Worker는 업무 결과를 관련 도메인 상태와
`BackgroundJob`에 durable하게 반영한 뒤 메시지를 acknowledge/delete한다. 처리 결과를
확정하지 못한 재시도 가능 오류에서는 삭제하지 않는다. 따라서 Worker가 메시지를
삭제할 수 있는 최소 조건은 durable한 종결 또는 이미 처리 중이라는 DB 결정을 확인한
경우다.

Python Worker는 이 저장소 밖에서 구현되므로 이 계약을 Spring consumer 테스트로
대체하지 않는다. 외부 Worker 저장소에서 상태별 수신/삭제 테스트를 별도로 유지해야
한다.

## 조회와 공정성

공통 Application은 등록된 Publisher를 순회하며 타입별로 발행 가능한 작업을 조회한다.
현재의 타입 조건 쿼리를 유지해 타입마다 최대 20개를 가져온다.

이 방식은 전체 작업에서 20개만 가져오는 방식과 달리, AI 분석 작업이 적체되더라도 향후
예약 결제 작업이 계속 선택되도록 한다. 작업 타입 수가 작은 현재 단계에서는 타입별
쿼리 비용보다 작업 간 공정성이 중요하다.

조회 조건은 다음과 같다.

- `status=PENDING`
- `next_retry_at`이 없거나 현재 시각 이전
- `requested_at` 오름차순

스케줄 실행 초반에는 다음 조건의 고착 작업도 타입별로 조회해 복구한다.

- `status=PUBLISHING`
- `publish_claim_until`이 현재 시각 이전

만료 claim은 즉시 외부 호출하지 않는다. 짧은 CAS 트랜잭션으로 실패 횟수를 증가시키고,
claim 필드를 비운 뒤 `PENDING + next_retry_at`으로 돌려 백오프 정책을 적용한다.
재시도 소진 상태라면 `FAILED`로 전이한다.

후보 ID 조회와 실제 CAS 사이에는 시간이 있으므로 CAS 쿼리가 조건을 다시 검증한다.

- 신규 선점: `id + status=PENDING + next_retry_at 도래`
- 만료 복구:
  `id + status=PUBLISHING + 조회 시 관찰한 publish_claim_id 일치
  + publish_claim_until <= recoveryNow`

신규 선점과 만료 복구 모두 영향 행 수가 정확히 1일 때만 성공으로 취급한다. 후보 조회
결과가 stale이면 영향 행 수가 0이 되어 아무 상태도 변경하지 않는다.

## 스케줄 설정

공통 properties의 기본값은 발행 비활성과 5초다.

```properties
app.background-job.publisher.enabled=false
app.background-job.publisher.fixed-delay-ms=5000
app.background-job.publisher.claim-duration-ms=60000
app.aws.sqs.api-call-timeout-ms=20000
app.aws.sqs.api-call-attempt-timeout-ms=10000
```

dev/prod에서는 발행을 활성화한다.

```properties
app.background-job.publisher.enabled=true
```

스케줄러는 다음 설정을 사용한다.

```java
@ConditionalOnProperty(
        name = "app.background-job.publisher.enabled",
        havingValue = "true"
)
@Scheduled(
        fixedDelayString = "${app.background-job.publisher.fixed-delay-ms:5000}"
)
@SchedulerLock(
        name = "backgroundJobPublishScheduler",
        lockAtMostFor = "PT1M"
)
```

`enabled=false`이면 API가 `BackgroundJob(PENDING)`을 저장하는 동작은 유지되고 큐 발행만
중단된다. 다시 활성화하면 쌓인 PENDING 작업을 오래된 순서부터 발행한다.

이 설정은 다음 용도로 사용한다.

- local/test의 외부 SQS 호출 차단
- 운영 장애 시 발행 중지
- DB/Worker 점검 중 일시 정지
- 향후 API 인스턴스와 Publisher 인스턴스 역할 분리

모든 운영 인스턴스에서 비활성화되면 작업이 PENDING으로 적체되므로, 향후
PENDING 개수와 최장 대기 시간에 대한 모니터링을 추가한다.

모든 활성 인스턴스는 같은 ShedLock 이름을 사용한다. 비관적 DB lock이나
`SELECT ... FOR UPDATE SKIP LOCKED`를 사용하지 않고 ShedLock으로 공통 스케줄러 실행을
1차 단일화한다. 행 단위 CAS claim은 ShedLock 만료, 수동 실행, 향후 다른 발행 진입점에
대한 2차 방어다.

claim duration은 정상적인 Publisher의 최대 외부 호출 시간보다 길어야 한다. SQS 호출
timeout은 claim duration보다 짧게 설정한다. 외부 호출 시간이 claim duration을 넘으면
정상 실행도 고착 작업으로 오인될 수 있으므로 timeout과 lease를 함께 조정한다.
AWS SDK v2 `SqsClient`에는 `ClientOverrideConfiguration`의 `apiCallTimeout`과
`apiCallAttemptTimeout`을 명시해 전체 호출과 개별 시도가 lease 전에 종료되게 한다.

## 상태 전이와 실패 처리

공통 Application이 상태 전이와 발행 재시도를 소유한다.

- 선점 성공: `PENDING → PUBLISHING`, claim ID와 만료 시각 기록
- 발행 성공: `PUBLISHING → QUEUED`, claim 필드 제거
- 일시 실패: `PUBLISHING → PENDING`, claim 필드 제거, 다음 재시도 예약
- claim 만료: `PUBLISHING → PENDING`, 실패 횟수 증가와 백오프 적용
- 재시도 간격: 1분, 2분, 4분, 8분
- 5번째 실패: `PUBLISHING → FAILED`, `error_code=PUBLISH_FAILED`
- 최종 실패 후 해당 Publisher의 `onPublishExhausted(...)` 호출

Processor의 구체적인 경계는 다음과 같다. 외곽의 `NOT_SUPPORTED` 템플릿은 호출자의
트랜잭션을 한 건 처리 전체에서 중단하고, 내부의 DB 템플릿은 매 상태 변경마다
`REQUIRES_NEW` 트랜잭션을 연다.

```text
TransactionTemplate(NOT_SUPPORTED):
  TransactionTemplate(REQUIRES_NEW):
    PENDING → PUBLISHING 선점 CAS
    rowCount=0이면 건너뜀

  활성 트랜잭션 없음:
    Publisher 메시지 준비 및 SQS 호출
    단, 구현체의 필요한 DB 읽기는 자체 짧은 트랜잭션 사용

  TransactionTemplate(REQUIRES_NEW):
    성공 시 PUBLISHING → QUEUED CAS
    실패 시 PUBLISHING → PENDING 또는 FAILED CAS
```

JPQL `@Modifying` 쿼리는 영향 행 수를 `int`로 반환한다. 성공/실패 상태 변경은
`id + status=PUBLISHING + publish_claim_id`가 모두 일치할 때만 수행한다. bulk update가
영속성 컨텍스트를 우회하므로 Processor는 같은 Job의 관리 엔티티를 상태 변경에
혼용하지 않고 CAS 결과와 불변 snapshot을 사용한다.

JPA Auditing EntityListener도 bulk update에는 적용되지 않으므로 각 CAS 쿼리는
`updated_at`을 명시적으로 현재 시각으로 변경한다.

5번째 실패 시 `BackgroundJob.FAILED`를 먼저 커밋한 뒤
`onPublishExhausted(...)`를 호출한다. 후처리 실패는 이미 커밋된 Job 상태를 되돌리지
않는다. 분석 리포트 후처리 실패로 Job과 Report 상태가 어긋나면 ERROR 로그와 향후
운영 모니터링으로 탐지한다.

lease 만료 복구가 5번째 실패를 만들어 `FAILED`로 전이한 경우에도 동일하게, 상태
커밋이 끝난 뒤 해당 Publisher의 `onPublishExhausted(...)`를 호출한다.

SQS 발행 성공 직후 DB 상태 기록 전에 프로세스가 종료되면 같은 메시지가 다시 발행될 수
있다. 따라서 전달 보장은 at-least-once이며 Worker는 `jobId`를 기준으로 멱등하게
처리해야 한다.

발행 재시도와 Worker의 업무 처리 재시도는 분리한다. 예를 들어 결제사의 일시 장애,
카드 거절, 이미 처리된 결제는 Spring 결제 Worker가 결제 도메인 정책에 따라 구분하며
공통 Publisher가 해석하지 않는다.

## 큐와 실행 역할

전송 기술은 SQS로 통일하지만 소비 런타임이 다른 작업은 물리 큐를 분리한다.

```text
ANALYSIS_REPORT    → analysis-report-queue    → Python Worker
SCHEDULED_PAYMENT  → scheduled-payment-queue  → Spring Worker
```

Spring API와 Spring Worker는 같은 코드베이스와 빌드 산출물을 사용할 수 있다. 초기에는
한 프로세스에서 두 역할을 모두 활성화하고, 부하나 장애 격리가 필요해지면 설정으로
API와 Worker 인스턴스를 분리한다. Publisher 활성화 설정과 Worker 소비 활성화 설정은
서로 독립적으로 관리한다.

## 테스트

다음 동작을 검증한다.

- 작업 타입에 맞는 Publisher 선택
- 같은 타입의 Publisher 중복 등록 시 시작 실패
- 지원 구현체가 없는 `BackgroundJobType` 존재 시 시작 실패
- 독립 스레드와 독립 트랜잭션의 실제 동시 선점 경쟁에서 하나의 claim CAS만 성공
- stale 후보 조회 후 `next_retry_at`이 미래로 변경되면 선점 CAS가 거절됨
- 발행 성공 시 메시지 전송과 `QUEUED` 전이 및 claim 필드 제거
- 현재 claim ID가 아닌 과거 실행자의 성공/실패 CAS 거절
- 일시 실패 시 `PENDING` 복귀, claim 제거, retry count와 다음 재시도 시각 기록
- claim 만료 시 고착 `PUBLISHING` 작업을 `PENDING` 또는 `FAILED`로 복구
- stale 만료 복구 snapshot의 claim ID가 바뀌면 복구 CAS가 거절됨
- 재시도 시각이 도래하지 않은 작업 제외
- 5번째 발행 실패 시 Job과 AnalysisReport의 `FAILED` 전이
- lease 만료 복구로 재시도 소진 시에도 AnalysisReport의 `FAILED` 전이
- 최종 실패 callback의 DB 반영이 실패해도 Job의 `FAILED` 전이는 유지됨
- 특정 작업의 실패가 다음 작업의 발행을 막지 않음
- 트랜잭션이 활성화된 호출자에서 Processor를 호출해도 외부 호출 동안 트랜잭션이 중단됨
- SQS 외부 호출 시 공통 DB 트랜잭션이 열려 있지 않음
- 격리 Spring Context의 `PROXY_METHOD` Scheduler proxy와 실제 `LockProvider`를 통해
  두 동시 호출 중 한 실행만 Application에 진입함
- local/test에서 공통 Scheduler 비활성

기존 `PublishAnalysisReportJobIntegrateTest`는 공통 Application을 호출하도록 변경하되
분석 리포트 메시지와 상태 전이에 대한 기존 검증을 유지한다. 구현 후 전체
`./gradlew test`를 실행한다.

## 문서 변경

다음 문서를 같은 변경 단위에서 갱신한다.

- 루트 `README.md`: 공통 BackgroundJob Publisher Scheduler 설명
- `api/backgroundjob/AGENTS.md`, `CLAUDE.md`: 공통 발행 책임과 재시도 정책
- `api/analysisreport/AGENTS.md`, `CLAUDE.md`: 전용 Scheduler 대신 Publisher 구현체 사용
- `docs/optimistic-lock-and-ddd.md`: Publisher도 status/claim 기반 CAS를 사용하도록 결정 갱신
