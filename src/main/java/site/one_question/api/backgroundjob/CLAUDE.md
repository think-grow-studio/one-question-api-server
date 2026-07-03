# backgroundjob 도메인 — 백그라운드 작업

코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.

## 불변식

- `BackgroundJob`은 외부 비동기 큐(SQS 등)로 발행될 작업의 영속 상태를 나타낸다.
- 새 작업은 `PENDING` 상태로 생성한다.
- `job_data`는 발행자가 읽을 JSON 문자열이며, 작업 타입별 application 레이어에서 payload 구조를 결정한다. 생성 시점에 확정되는 불변 값이다 — 이후 갱신하지 않는다.
- 멱등성 범위는 작업 생성 컨텍스트별로 정한다. AI 분석 리포트는 `(member_id, job_type, idempotency_key)`를 유니크하게 사용한다.
- `idempotency_key`는 클라이언트가 생성 요청마다 제공한다. 서버에서 임의 UUID로 대체하지 않으며, 형식 검증과 정규화는 `IdempotencyKey` 값 객체가 담당한다.
- `request_hash`는 같은 멱등키 재사용 시 요청 payload 동일성을 검증하기 위한 값이다. 형식 검증과 SHA-256 생성은 `RequestHash` 값 객체가 담당하며, 중복 작업 판정 기준으로 단독 사용하지 않는다.
- 상태 전이는 큐 발행/처리 구현에서 명시적으로 관리한다. 엔티티 필드를 직접 우회 갱신하지 않는다.
