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
  → JobType별 Publisher
  → 목적지 큐 발행
  → BackgroundJob(ENQUEUED)
```

큐 발행 실패는 공통 발행 계층이 재시도한다. 큐 발행 이후 AI 분석 실패나 결제 실패는
각 Worker와 업무 도메인의 처리 정책이다.

## 구성요소

### backgroundjob 컨텍스트

```text
api/backgroundjob/
├── application/
│   ├── BackgroundJobPublishApplication
│   ├── BackgroundJobPublishProcessor
│   ├── BackgroundJobPublishExhaustedProcessor
│   └── PublishAttemptResult
├── domain/
│   ├── BackgroundJob
│   ├── BackgroundJobType
│   ├── BackgroundJobRepository
│   ├── BackgroundJobService
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
    void publish(BackgroundJob job);
    default void onPublishExhausted(Long jobId, Exception cause) {}
}
```

- `supportedType()`: 담당하는 작업 타입
- `publish(job)`: 메시지 조립과 외부 큐 발행
- `onPublishExhausted(...)`: 공통 발행 재시도 소진 시 필요한 도메인 후처리

`BackgroundJobPublisherRegistry`는 Spring이 주입한 Publisher 목록을
`BackgroundJobType`으로 매핑한다. 같은 타입을 지원하는 구현체가 둘 이상이거나 enum에
대응하는 구현체가 없으면 시작 시 실패시킨다. Dispatcher에 작업 타입별 `switch`는 두지
않는다.

`BackgroundJobPublishApplication`은 타입별 발행 대상 ID를 조회하고
`BackgroundJobPublishProcessor`에 한 건씩 위임한다. Processor는 각 작업을 독립
트랜잭션에서 다시 조회해 Publisher 실행과 상태 전이를 처리한다. 별도 bean으로 분리해
Spring의 `REQUIRES_NEW` 트랜잭션 프록시가 실제로 적용되게 한다.

Processor가 최종 발행 실패를 커밋하면 `PublishAttemptResult.EXHAUSTED`를 반환한다.
Application은 Processor 호출이 반환되어 해당 트랜잭션이 커밋된 뒤, 별도의
`REQUIRES_NEW` 후처리 bean을 통해 Publisher의 `onPublishExhausted(...)`를 호출한다.

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

1. `BackgroundJob`으로 `AnalysisReport` 역조회
2. `job_data`와 `jobId`, `analysisReportId`, `traceId`로 메시지 생성
3. 분석 리포트 전용 SQS 큐로 발행
4. 발행 재시도 소진 시 `AnalysisReport.FAILED` 전이

분석 리포트 큐 URL과 메시지 계약은 계속 `analysisreport` 컨텍스트가 소유한다.

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

## 스케줄 설정

공통 properties의 기본값은 발행 비활성과 5초다.

```properties
app.background-job.publisher.enabled=false
app.background-job.publisher.fixed-delay-ms=5000
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

모든 활성 인스턴스는 같은 ShedLock 이름을 사용한다. 현재처럼 DB claim이나
`SELECT ... FOR UPDATE SKIP LOCKED`를 추가하지 않고 ShedLock으로 공통 스케줄러 실행을
단일화한다. `lockAtMostFor` 안에 타입별 배치 발행이 끝나는 것을 운영 전제로 하며,
발행 시간이 이 한도를 반복적으로 넘으면 배치 크기 또는 lock 시간을 조정한다.

## 상태 전이와 실패 처리

공통 Application이 상태 전이와 발행 재시도를 소유한다.

- 발행 성공: `PENDING → ENQUEUED`
- 일시 실패: `retry_count` 증가, `PENDING` 유지, 다음 재시도 예약
- 재시도 간격: 1분, 2분, 4분, 8분
- 5번째 실패: `PENDING → FAILED`, `error_code=PUBLISH_FAILED`
- 최종 실패 후 해당 Publisher의 `onPublishExhausted(...)` 호출

각 작업은 `BackgroundJobPublishProcessor`의 `REQUIRES_NEW` 트랜잭션에서 처리한다.
작업 하나의 발행 실패나 DB 상태 반영 실패가 앞뒤 작업의 트랜잭션을 롤백하지 않게
격리한다. Publisher 예외는 Processor가 잡아 같은 작업 트랜잭션 안에서 재시도 상태로
전환한다.

5번째 실패 시 `BackgroundJob.FAILED` 전이를 작업 트랜잭션에서 먼저 커밋한다. 커밋된
뒤 `onPublishExhausted(...)`를 별도 `REQUIRES_NEW` 트랜잭션으로 호출한다. 후처리
트랜잭션이 실패하면 해당 오류를 별도로 기록하되 이미 커밋된 Job의 FAILED 전이는
되돌리지 않는다. 분석 리포트 후처리 실패로 Job과 Report 상태가 어긋난 경우는 ERROR
로그와 향후 운영 모니터링으로 탐지한다.

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
- 발행 성공 시 메시지 전송과 `ENQUEUED` 전이
- 일시 실패 시 `PENDING` 유지, retry count와 다음 재시도 시각 기록
- 재시도 시각이 도래하지 않은 작업 제외
- 5번째 발행 실패 시 Job과 AnalysisReport의 `FAILED` 전이
- 최종 실패 callback의 DB 반영이 실패해도 Job의 `FAILED` 전이는 유지됨
- 특정 작업의 실패가 다음 작업의 발행을 막지 않음
- 앞 작업 발행 후 다음 작업에서 예외가 발생해도 앞 작업의 `ENQUEUED` 전이가 유지됨
- 두 인스턴스가 동시에 스케줄 실행을 시도해도 ShedLock으로 한 실행만 발행함
- local/test에서 공통 Scheduler 비활성

기존 `PublishAnalysisReportJobIntegrateTest`는 공통 Application을 호출하도록 변경하되
분석 리포트 메시지와 상태 전이에 대한 기존 검증을 유지한다. 구현 후 전체
`./gradlew test`를 실행한다.

## 문서 변경

다음 문서를 같은 변경 단위에서 갱신한다.

- 루트 `README.md`: 공통 BackgroundJob Publisher Scheduler 설명
- `api/backgroundjob/AGENTS.md`, `CLAUDE.md`: 공통 발행 책임과 재시도 정책
- `api/analysisreport/AGENTS.md`, `CLAUDE.md`: 전용 Scheduler 대신 Publisher 구현체 사용
