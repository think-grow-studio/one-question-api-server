# backgroundjob 도메인 — 백그라운드 작업

이 컨텍스트를 수정할 때는 루트 `README.md`의 레이어 규칙을 따른다.

## 불변식

- `BackgroundJob`은 외부 비동기 큐(SQS 등)로 발행될 작업의 영속 상태를 나타낸다.
- 새 작업은 `PENDING` 상태로 생성한다.
- `payload`는 발행자·워커가 읽을 JSON 문자열이며 **도메인 행에 존재하지 않는 커맨드 파라미터만** 담는다. 담을 것이 없으면 `BackgroundJob.EMPTY_PAYLOAD`(`{}`)를 저장한다 — NULL 금지. 소비자(특히 Python 워커)에 null 분기가 영구히 생기는 것을 막기 위함이다. 생성 시점에 확정되는 불변 값이다. 값 객체로 감싸지 않는 이유는 직렬화를 대신해주지 못해 껍데기만 늘기 때문이다 — 파라미터가 있는 job 타입은 어차피 밖에서 JSON 문자열을 만들어 넘긴다.
- `reference_id`는 이 커맨드가 대상으로 하는 도메인 애그리거트의 id다. 대상 타입은 `job_type`이 결정하는 폴리모픽 참조이므로 FK를 걸지 않는다. 대상 애그리거트가 없는 job 타입(배치·집계)은 NULL이다. 생성 후 갱신하지 않는다. 커맨드가 대상을 가리키고, 대상은 자기를 만든 커맨드를 모른다.
- `uk_background_job_reference`가 대상 애그리거트 1건당 job 1건을 강제한다. Oracle 실제 인덱스는 `(CASE WHEN reference_id IS NULL THEN NULL ELSE job_type END, reference_id)` 함수 기반이라 `reference_id IS NULL` 행을 인덱스에서 제외한다(Oracle은 부분 NULL 복합 유니크에서 중복을 거부하므로 필요한 관용구). 엔티티에는 평범한 `(job_type, reference_id)` `@UniqueConstraint`로 선언돼 있어 H2 테스트가 제약을 덮는다. 형태가 달라도 부팅이 깨지지 않는 이유는 **`ddl-auto=validate`가 테이블/컬럼/타입만 검증하고 제약·인덱스는 보지 않기 때문**이다. PostgreSQL로 이전하면 `... (job_type, reference_id) WHERE reference_id IS NOT NULL`로 단순화한다.
- `reference_type` 컬럼은 두지 않는다. 현재 `job_type`이 대상 타입을 100% 결정한다. **추가해야 하는 신호는 하나의 `job_type`이 두 종류 이상의 엔티티를 가리켜야 할 때**다(예: `SEND_PUSH`가 상황에 따라 `daily_question_answer` 또는 `answer_post`를 가리켜야 하는 경우). 서로 다른 job_type이 같은 엔티티 타입을 가리키는 것은 트리거가 아니다 — `job_type IN (...)`으로 조회된다. 추가 시 `CHECK ((reference_type IS NULL) = (reference_id IS NULL))`이 필요하고, 유니크를 `(reference_type, reference_id)`로 옮기면 job_type이 다른 재분석 job이 차단되므로 재검토해야 한다.
- `member_id`는 NOT NULL FK로 유지한다. 주인 없는 시스템 job이 필요해지면 nullable로 만들지 말고 **시스템 계정 행**을 만든다(`AuthSocialProvider`에 `SYSTEM` 추가 + 시드 1행 + `isHuman()` 헬퍼로 회원 집계 제외 로직 일원화). nullable로 만들면 `uk_background_job_idempotency`가 PostgreSQL/MySQL에서 조용히 무력화된다 — DB는 NULL끼리 같다고 보지 않으므로 `(NULL, 'X', 'key')` 두 행이 모두 저장된다. 참고: `AuthSocialProvider.AI_PERSONA`는 운영 DB에 사용된 적이 없어 선례가 아니다. 즉 시스템 계정을 도입하면 "사람 아닌 member 행을 집계에서 제외하는 규율"을 처음부터 세우는 것이다.
- 멱등성 범위는 작업 생성 컨텍스트별로 정한다. AI 분석 리포트는 `(member_id, job_type, idempotency_key)`를 유니크하게 사용한다.
- `idempotency_key`는 클라이언트가 생성 요청마다 제공한다. 서버에서 임의 UUID로 대체하지 않으며, 형식 검증과 정규화는 `IdempotencyKey` 값 객체가 담당한다.
- `request_hash`는 같은 멱등키 재사용 시 요청 payload 동일성을 검증하기 위한 값이다. 형식 검증과 SHA-256 생성은 `RequestHash` 값 객체가 담당하며, 중복 작업 판정 기준으로 단독 사용하지 않는다.
- 발행 상태는 `PENDING → PUBLISHING → QUEUED`로 전이한다. 신규 선점 CAS는 `id + PENDING + publish_scheduled_at 도래`, 완료·실패 CAS는 `id + PUBLISHING + publish_claim_id`, 만료 복구 CAS는 여기에 claim 만료 시각까지 확인한다. 모든 bulk CAS는 `updated_at`을 직접 갱신한다.
- `publish_claim_id`는 발행 시도마다 생성하는 UUID fencing token이다. `publish_claim_until`은 중단된 `PUBLISHING` 작업을 복구할 수 있는 lease 만료 시각이다.
- **컬럼 접두사가 소유 단계를 뜻한다.** `publish_*`는 발행 단계(이 서버만 씀), `process_*`는 처리 단계(워커만 씀), 접두사 없는 것(`status`·`finished_at`·`error_code`·`error_reason`)은 생명주기 전체로 **그 작업을 끝낸 쪽이 기록**하며 일생에 한 번만 값이 생긴다. 단계별로 쪼개지 않는 이유는 쪼개면 한쪽이 항상 NULL이기 때문이다.
- 발행자는 `status=PENDING AND publish_scheduled_at <= now`인 작업만 `publish_scheduled_at` 순으로 집는다. **재시도 시각 컬럼을 따로 두지 않는다** — 발행 실패 시 `publish_scheduled_at`을 백오프만큼 미뤄 갱신하므로 첫 발행과 재발행이 같은 조건 하나로 조회된다. 클라이언트향 "요청 시각"은 `created_at`이 담는다. 만료된 `PUBLISHING`도 현재 claim ID와 만료 시각을 재확인하는 CAS로 복구한다.
- `publish_attempt_count`는 **선점 CAS 시점에** 증가한다(첫 시도 포함, 1부터). 실패 시가 아니라 시도 시에 세므로 첫 시도에 성공한 작업도 1이다. `0`은 아직 시도한 적이 없다는 뜻이다. `PublishRetryPolicy`는 이미 증가된 값을 받아 소진 판정만 하며 다시 더하지 않는다.
- 발행 성공 시 `QUEUED`. 실패 시 지수 백오프(1·2·4·8분)로 `publish_scheduled_at` 재예약, 5회 소진 시 FAILED(`error_code=PUBLISH_FAILED`). `error_reason`은 255자로 잘라 저장한다. **소진 시에는 `publish_scheduled_at`을 옮기지 않는다** — FAILED는 어차피 발행 대상이 아니므로, JPQL에서 `COALESCE(:publishScheduledAt, job.publishScheduledAt)`로 "null이면 유지"한다.
- `process_claim_id`/`process_claim_until`/`process_attempt_count`/`process_started_at`은 **워커 소유다.** 이 서버는 컬럼만 매핑하고 읽지도 쓰지도 않는다. `process_attempt_count`는 워커가 SQS `ApproximateReceiveCount`를 그대로 기록하는 관측용이며 terminal 판정 조건으로 쓰지 않는다. `process_started_at`도 관측용이고 소유권 판단은 `process_claim_id`가 담당한다.
- **실패 조사 규칙**: `status=FAILED`일 때 `process_started_at IS NULL`이면 발행 단계에서, NOT NULL이면 처리 단계에서 죽은 것이다. `error_code`/`error_reason`은 terminal 실패에만 기록한다.
- 공통 `BackgroundJobPublishScheduler`는 기본 5초 주기이며 `app.background-job.publisher.enabled=true`인 프로필에서만 활성화한다.
- 외부 큐 발행은 DB 트랜잭션 밖에서 수행한다. `BackgroundJobPublishProcessor`는 선점·완료·실패 상태 변경만 각각 짧은 `REQUIRES_NEW` 트랜잭션으로 처리하고 그 사이 SQS 호출은 트랜잭션 밖에서 실행한다. 따라서 발행 진입점은 트랜잭션 밖에서 호출해야 하며, 스케줄러 진입점이 이를 보장한다. SQS 발행 직후 최종 상태 기록 전에 장애가 나면 메시지가 중복될 수 있으므로 Worker는 `jobId`로 멱등 처리한다.
- SQS 메시지는 Publisher가 `PUBLISHING → QUEUED`를 커밋하기 전에 Worker에게 보일 수 있다. Worker가 `PENDING` 또는 `PUBLISHING`을 읽으면 조기 수신으로 판단해 업무를 시작하지 않고 acknowledge/delete하지 않으며, visibility timeout 후 재전달되게 둔다.
- Worker는 `QUEUED → PROCESSING` CAS에 성공한 경우만 업무를 시작한다. `PROCESSING` 또는 종결 상태(`SUCCEEDED`/`FAILED`)를 읽은 중복 수신은 durable한 처리 소유권/종결 결정을 확인한 것이므로 반복 처리하지 않고 acknowledge/delete할 수 있다. 선점 Worker는 처리 결과를 durable하게 반영한 뒤에만 acknowledge/delete하며, 결과가 확정되지 않은 재시도 가능 오류에서는 삭제하지 않는다.
