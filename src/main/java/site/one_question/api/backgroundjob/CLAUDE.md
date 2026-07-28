# backgroundjob 도메인 — 백그라운드 작업

코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.

## 불변식

- `BackgroundJob`은 외부 비동기 큐(SQS 등)로 발행될 작업의 영속 상태를 나타낸다.
- 새 작업은 `PENDING` 상태로 생성한다.
- `job_data`는 발행자가 읽을 JSON 문자열이며, 작업 타입별 application 레이어에서 payload 구조를 결정한다. 생성 시점에 확정되는 불변 값이다 — 이후 갱신하지 않는다.
- 멱등성 범위는 작업 생성 컨텍스트별로 정한다. AI 분석 리포트는 `(member_id, job_type, idempotency_key)`를 유니크하게 사용한다.
- `idempotency_key`는 클라이언트가 생성 요청마다 제공한다. 서버에서 임의 UUID로 대체하지 않으며, 형식 검증과 정규화는 `IdempotencyKey` 값 객체가 담당한다.
- `request_hash`는 같은 멱등키 재사용 시 요청 payload 동일성을 검증하기 위한 값이다. 형식 검증과 SHA-256 생성은 `RequestHash` 값 객체가 담당하며, 중복 작업 판정 기준으로 단독 사용하지 않는다.
- 발행 상태는 `PENDING → PUBLISHING → QUEUED`로 전이한다. 신규 선점 CAS는 `id + PENDING + 재시도 시각 도래`, 완료·실패 CAS는 `id + PUBLISHING + publish_claim_id`, 만료 복구 CAS는 여기에 claim 만료 시각까지 확인한다. 모든 bulk CAS는 `updated_at`을 직접 갱신한다.
- `publish_claim_id`는 발행 시도마다 생성하는 UUID fencing token이다. `publish_claim_until`은 중단된 `PUBLISHING` 작업을 복구할 수 있는 lease 만료 시각이다.
- 발행자는 `status=PENDING`이고 실효 발행 시각 `COALESCE(next_retry_at, scheduled_at)`이 도래한 작업만 `scheduled_at` 순으로 집는다. `scheduled_at`은 최초 발행 예정 시각(생성 시 현재 시각, 지연 발행 잡은 미래)이고, 재시도 시엔 `next_retry_at`이 백오프로 이를 덮는다. 클라이언트향 "요청 시각"은 `created_at`이 담는다. 만료된 `PUBLISHING`도 현재 claim ID와 만료 시각을 재확인하는 CAS로 복구한다.
- 발행 성공 시 `QUEUED`. 실패 시 retry_count 증가 + 지수 백오프(1·2·4·8분) 재예약, 5회 소진 시 FAILED(`error_code=PUBLISH_FAILED`). `error_reason`은 255자로 잘라 저장한다.
- 공통 `BackgroundJobPublishScheduler`는 기본 5초 주기이며 `app.background-job.publisher.enabled=true`인 프로필에서만 활성화한다.
- 외부 큐 발행은 DB 트랜잭션 밖에서 수행한다. `BackgroundJobPublishProcessor`는 선점·완료·실패 상태 변경만 각각 짧은 `REQUIRES_NEW` 트랜잭션으로 처리하고 그 사이 SQS 호출은 트랜잭션 밖에서 실행한다. 따라서 발행 진입점은 트랜잭션 밖에서 호출해야 하며, 스케줄러 진입점이 이를 보장한다. SQS 발행 직후 최종 상태 기록 전에 장애가 나면 메시지가 중복될 수 있으므로 Worker는 `jobId`로 멱등 처리한다.
- SQS 메시지는 Publisher가 `PUBLISHING → QUEUED`를 커밋하기 전에 Worker에게 보일 수 있다. Worker가 `PENDING` 또는 `PUBLISHING`을 읽으면 조기 수신으로 판단해 업무를 시작하지 않고 acknowledge/delete하지 않으며, visibility timeout 후 재전달되게 둔다.
- Worker는 `QUEUED → PROCESSING` CAS에 성공한 경우만 업무를 시작한다. `PROCESSING` 또는 종결 상태(`SUCCEEDED`/`FAILED`)를 읽은 중복 수신은 durable한 처리 소유권/종결 결정을 확인한 것이므로 반복 처리하지 않고 acknowledge/delete할 수 있다. 선점 Worker는 처리 결과를 durable하게 반영한 뒤에만 acknowledge/delete하며, 결과가 확정되지 않은 재시도 가능 오류에서는 삭제하지 않는다.
