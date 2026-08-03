# analysis_report → background_job 의존성 제거 설계

작성일: 2026-07-30
대상 브랜치: `feature/ai-report`

## 배경

현재 `analysis_report.background_job_id`가 `NOT NULL UNIQUE` FK로 `background_job(id)`를 가리킨다.
도메인 애그리거트가 "나는 백그라운드 작업으로 만들어졌다"는 인프라 사실을 스키마에 박아두고 있고,
그 결과 `analysisreport` 컨텍스트가 `backgroundjob` 컨텍스트를 참조한다.

범용 job 테이블 + 도메인 전용 큐 + **커맨드 큐** 전제에서는 방향이 반대여야 한다.
커맨드가 대상을 가리키고, 대상은 자기를 만든 커맨드를 모른다.

부수 효과로 생성 순서 제약도 사라진다. 지금은 FK 때문에 job을 먼저 만들어야 하지만,
역전 후에는 report → job 순으로 자연스럽게 만들 수 있다.

## 결정 사항

| # | 결정 | 이유 |
|---|---|---|
| 1 | `analysis_report.background_job_id` 삭제 (FK·유니크 포함) | 도메인 → 인프라 역참조 제거 |
| 2 | `background_job.reference_id NUMBER` nullable 추가, **FK 없음** | 커맨드가 대상 애그리거트를 가리킨다. 폴리모픽이라 FK 불가 |
| 3 | `job_type` 유지 (`reference_type`으로 rename 안 함) | 커맨드 종류 ≠ 참조 대상 타입. publisher 라우팅 키 + 멱등 유니크 키 구성요소 |
| 4 | `reference_type` 컬럼 추가 안 함 | 현재 `job_type`에 100% 함수 종속. 추가 트리거는 아래 참조 |
| 5 | `job_data` → `payload` rename, **NOT NULL 유지** | 업계 관행(Sidekiq/river/pg-boss 모두 NOT NULL + 빈 값 `{}`) |
| 6 | ANALYSIS_REPORT의 `payload` = `{}` | 기존 `{memberId, reportType}`이 전부 다른 컬럼의 복사본이었음 |
| 7 | 1:1 제약 유지 — `uk_background_job_reference` 함수 기반 유니크 인덱스 | 재분석은 새 리포트 행 → 새 job이므로 1:1이 재분석을 막지 않음 |
| 8 | 마이그레이션 13번 파일 **직접 수정** (14번 신설 안 함) | 운영 이력 없음. DROP 후 재생성하므로 rename 이력이 남을 필요 없음 |

### 무변경 항목 (명시)

- **`request_hash`** — 요청 본문 SHA-256. 같은 멱등키로 다른 내용을 보냈는지 검증하는 용도이며,
  중복 판정 기준이 아니다. 계산 방식·용도 모두 그대로.
- **`member_id`** — `NOT NULL` FK 유지. 시스템 job 도입 시 시스템 계정 방식으로 간다
  (아래 "`member_id` 스코프" 절에서 확정).
- **`uk_background_job_idempotency`** — `(member_id, job_type, idempotency_key)` 그대로.
- **`BackgroundJobMessage`** — `{jobId, correlationId}` 그대로. `reference_id`를 메시지에 넣지 않는다.
  Claim Check 패턴 유지. 워커는 상태 CAS 때문에 어차피 job 행을 읽어야 하므로 아껴지는 쿼리가 없다.
  `correlationId`는 job 행을 **읽기 전에** 로그 컨텍스트를 세팅해야 하므로 메시지에 남긴다
  (DB 조회 실패 로그가 correlation을 가장 필요로 한다).

### `reference_id`를 NUMBER로 하는 이유

`String(256)`도 검토했으나 NUMBER를 택했다.

- 현재·예상 referent 전부 IDENTITY `Long` PK다.
- Oracle에서 `JOIN analysis_report r ON r.id = j.reference_id`가 varchar면 암묵 형변환이 일어나
  `reference_id` 인덱스를 타지 못한다. 운영 인덱스라는 목적 자체를 깎는다.
- string → Long 파싱 경계와 "쓰레기 값이 들어갈 수 있음"이 사라진다.
- 탈출구가 이미 있다: 훗날 숫자가 아닌 참조가 필요한 job 타입은 `payload`에 넣는다.

### `payload`의 역할 정의

**`payload` = 도메인 행에 존재하지 않는 커맨드 파라미터.**

ANALYSIS_REPORT에는 그런 것이 없다:

| 기존 job_data 필드 | 실제 원천 |
|---|---|
| `memberId` | `background_job.member_id` 컬럼 |
| `reportId` | `background_job.reference_id` 컬럼 (신규) |
| `reportType` | `analysis_report.report_type` |

워커는 상태 가드(AI-REPORT-004)와 결과 기록 때문에 `analysis_report`를 반드시 읽고,
입력을 위해 `analysis_report_source`도 읽는다. 따라서 payload에 담아 아껴지는 쿼리는 0개다.
→ ANALYSIS_REPORT는 `{}`를 저장하고, 나중에 `{"promptVersion": "v2"}` 같은 진짜 파라미터가
생기면 그때 여기에 넣는다.

`NOT NULL`을 유지하는 이유: 소비자(특히 Python 워커)에 null 체크가 영구히 붙는 것을 막는다.
`json.loads(row.payload)` vs `json.loads(row.payload) if row.payload else {}`.
NULL과 `{}`이 서로 다른 의미를 갖지 않으므로 nullable로 둘 이유가 없다.

## 스키마

`migration/13. create_analysis_report_job_tables.sql`을 직접 수정한다.
dev DB에는 세 테이블을 수동 DROP한 뒤 재실행한다.

`background_job` 변경분:

```sql
    job_type        VARCHAR2(100)             NOT NULL,
    member_id       NUMBER                    NOT NULL,
    reference_id    NUMBER,                              -- 신규
    payload         CLOB                      NOT NULL,  -- job_data 에서 rename
```

추가 인덱스:

```sql
-- 리포트 1건당 job 1건. reference_id 가 NULL 이면 두 표현식이 모두 NULL 이 되어
-- 인덱스에서 제외된다 (Oracle 에서 partial unique index 를 만드는 관용구).
CREATE UNIQUE INDEX uk_background_job_reference ON background_job
    (CASE WHEN reference_id IS NULL THEN NULL ELSE job_type END, reference_id);
```

`analysis_report`에서 삭제할 것: `background_job_id` 컬럼, `fk_analysis_report_job`, `uk_analysis_report_job`.

### DB 이식성 참고

복합 유니크 인덱스의 NULL 취급이 DB마다 다르다. **Oracle만 예외**다.

| DB | 평범한 `UNIQUE (job_type, reference_id)` | partial index | 표현식 인덱스 |
|---|---|---|---|
| Oracle | ✗ 부분 NULL 행도 인덱싱 → `(TYPE, NULL)` 2개면 위반 | 미지원 | ✓ ← CASE 트릭 필요 |
| PostgreSQL | ✓ NULLS DISTINCT 기본 → 그냥 됨 | ✓ 가장 관용적 | ✓ |
| MySQL 8.0.13+ | ✓ | ✗ | ✓ |
| H2 (MySQL mode) | ✓ | ✗ | 제한적 |

PostgreSQL으로 이전하면 CASE 트릭이 불필요해진다. 그때는 아래로 단순화한다:

```sql
CREATE UNIQUE INDEX uk_background_job_reference
    ON background_job (job_type, reference_id) WHERE reference_id IS NOT NULL;
```

엔티티에는 `@UniqueConstraint(name = "uk_background_job_reference",
columnNames = {"job_type", "reference_id"})`를 선언한다. H2가 NULL을 서로 다르게 보므로
테스트에서 1:1 제약이 정상 동작하고, 제약을 테스트로 덮을 수 있다.
Oracle 실제 인덱스는 CASE 형태라 애노테이션과 다르지만, **Hibernate `ddl-auto=validate`는
테이블/컬럼/타입만 검증하고 제약·인덱스는 보지 않으므로** dev/prod 부팅이 깨지지 않는다.
이 사실에 의존하는 설계이므로 마이그레이션에 주석으로 남긴다.

## Java 변경

| 파일 | 변경 |
|---|---|
| `BackgroundJob` | `jobData` → `payload`, `Long referenceId` 추가, `create()` 시그니처, `@UniqueConstraint` 추가 |
| `ClaimedBackgroundJob` | `jobData` → `payload`, `referenceId` 추가 |
| `BackgroundJobPublisher` | `onPublishExhausted(Long jobId, …)` → `ClaimedBackgroundJob`을 받아 재조회 없이 `referenceId` 사용 |
| `AnalysisReport` | `backgroundJob` 필드 삭제, `createPending(member, reportType)` |
| `AnalysisReportRepository` / `AnalysisReportService` | `findByBackgroundJob` / `findByBackgroundJobId` 삭제 |
| `AnalysisReportApplication` | 생성 순서 report → job, `createJobData()` 삭제, 멱등 경로 `findById(job.getReferenceId())` |
| `AnalysisReportJobPublisher` | `onPublishExhausted` 시그니처 반영 |
| rename 반영 | `PendingPublishTarget`, `ExpiredPublishClaim`, `BackgroundJobService`, `BackgroundJobRepository`, `BackgroundJobPublishApplication`, `BackgroundJobRequestHashConflictException` |

`AnalysisReportApplication`은 `backgroundjob` 컨텍스트를 계속 참조한다 (job을 만드는 주체이므로 정상).
제거되는 것은 **`analysisreport` 도메인 레이어의** `backgroundjob` 의존이다.

## 데이터 흐름

**생성 (한 트랜잭션)**

1. 멱등키로 기존 job 조회 → 있으면 `validateSameRequestHash` 후
   `analysisReportService.findById(job.getReferenceId())`로 수렴 응답
2. 소스 답변 소유권 검증
3. `analysis_report(PENDING)` 저장 → id 확보
4. `background_job(PENDING, reference_id = report.id, payload = "{}")` 저장
5. `analysis_report_source` 스냅샷 저장

**발행** — 변경 없음. 스케줄러가 선점 → SQS `{jobId, correlationId}` → `QUEUED`.

**워커** — `jobId`로 job 행을 읽고 `reference_id`로 `analysis_report`를 조회한다
(기존에는 `background_job_id` 역방향 조회). 상태 CAS·조기 수신 처리 규약은 그대로.

**발행 재시도 소진** — `onPublishExhausted`가 `ClaimedBackgroundJob.referenceId()`로
리포트를 찾아 별도 짧은 트랜잭션에서 `fail()`.

## `member_id` 스코프 — 방식 A(시스템 계정) 확정

**결론: `member_id`는 `NOT NULL` FK로 유지한다. 주인 없는 시스템 job이 필요해지면
`member` 테이블에 시스템 계정 행을 만든다. 이번 변경에서 코드·스키마 변경은 없다.**

근거는 관행의 짝이 다음 두 가지로만 지어진다는 점이다.

- owner 컬럼이 있다 → `NOT NULL` 유지 + 시스템 계정
- owner 컬럼이 없다 → flat 네임스페이싱 dedup 키

"owner 컬럼을 nullable로 두고 flat 키를 쓴다"는 중간 형태는 어느 쪽도 아니다(아래 방식 B).

두 번째 부류(Temporal, SQS, Sidekiq, pg-boss)가 owner 컬럼이 없는 이유는 owner 컬럼이
나쁘기 때문이 아니라, **범용 큐 라이브러리라서 도메인 컬럼이 애초에 하나도 없기** 때문이다.
반면 이 프로젝트의 `background_job`은 우리가 소유한 애플리케이션 테이블이고,
`member_id`는 여기서 값을 한다 — 인덱스 타는 "이 사용자의 job" 조회, FK 무결성, 감사.
따라서 두 번째 부류는 이 테이블의 템플릿이 될 수 없고, 남는 선택은 A다.

아래는 판단 근거와 기각한 대안의 기록이다.

### 문제의 구조

현재 `member_id`는 두 역할을 겸한다.

| 역할 | NULL 되면 |
|---|---|
| A. 소유자 (조회·FK 무결성) | 괜찮음 |
| B. 멱등 유니크 키의 일부 `(member_id, job_type, idempotency_key)` | **깨짐** |

B가 깨지는 이유: DB는 NULL끼리 같다고 보지 않으므로, PostgreSQL/MySQL에서
`(NULL, 'DAILY_DIGEST', '2026-07-30')` 두 행이 **에러 없이 둘 다 저장된다.**

즉 시스템 job을 도입하면서 `member_id`를 nullable로 만들면 B가 조용히 깨진다.
그래서 "nullable로 완화한다"는 선택지는 멱등 유니크 키 변경을 반드시 동반한다.
A는 이 문제를 애초에 만들지 않는다.

### 방식 A — 시스템 계정 (`member_id` NOT NULL 유지) — **확정**

`AuthSocialProvider`에 `SYSTEM` 추가 + 시스템 member 행 1개 시드.

- **관행 측면에서 이쪽이 표준이다.** owner FK 컬럼을 가진 시스템들은 대체로 NOT NULL을 유지하고
  시스템 계정을 쓴다 (Stripe의 내부 account, 시스템 테넌트를 두는 멀티테넌트 SaaS,
  시스템 유저를 가리키는 `created_by`).
- 장점: NULL 의미론 문제가 원천적으로 없음. 멱등 유니크 키 무변경. 모든 행에 FK 무결성.
  감사 관점에서 "누가 요청했나"에 항상 답이 있음. 시스템 주체가 여러 개 필요해지면 행을 더 만들면 됨.
  **이번 작업량 0.**
- 단점: `AuthSocialProvider`는 *소셜 로그인 제공자* 열거형인데 `SYSTEM`은 소셜 로그인이 아님
  (인증 개념 오염, `provider`+`provider_id`가 로그인 조회 키라 그 공간에 가짜 항목이 앉음).
  NOT NULL 필드를 전부 조작해야 함(`public_id` unique, `full_name`, `provider_id`,
  `joined_at/date`, `permission`, `locale`, `status`). **모든 회원 수/목록/분석 쿼리에서 제외하는
  규율을 새로 도입해야 하고, 누락되면 에러 없이 통계가 틀린다.**

> 참고: `AuthSocialProvider.AI_PERSONA`가 선례로 보이지만 **실제 운영 DB에 사용된 적이 없다**
> (`AdminAnswerPostService.java:39`의 필터만 남아 있음). 선례로 취급하지 말 것.

**시스템 계정 도입 시 체크리스트** (시스템 job이 실제로 필요해질 때 수행)

1. `AuthSocialProvider`에 `SYSTEM` 추가
2. 시스템 member 행 1개 시드 (`public_id`, `full_name`, `provider_id`, `joined_at/date`,
   `permission`, `locale`, `status` 모두 NOT NULL이므로 값 확정 필요)
3. `AuthSocialProvider.isHuman()` 같은 헬퍼를 만들어 **제외 로직을 한 곳에 모은다.**
   회원 수/목록/분석 쿼리에서 시스템 계정을 빼는 규율이 흩어지면 조용히 통계가 틀린다.
   현재 `AdminAnswerPostService.java:39`가 provider 필터를 호출 지점에 직접 쓰고 있으므로
   그때 함께 정리한다.

### 방식 B — nullable FK + 멱등키 네임스페이싱 — **기각**

`member_id`를 nullable로 완화하고 유니크 키를 `(job_type, idempotency_key)`로 바꾼다.
스코프는 `IdempotencyKey` 값 객체가 키 문자열에 접어넣는다.

```java
IdempotencyKey.forMember(42L, "abc-123")            // "member:42:abc-123"
IdempotencyKey.forSystem("daily-digest", "2026-07-30") // "system:daily-digest:2026-07-30"
```

compact 생성자에 `^(member:\d+|system:[a-z0-9-]+):\S+$` 검증을 넣으면 스코프 없는 키를
만들 수 없으므로, 네임스페이싱 누락이 컴파일/런타임 경계에서 차단된다.
`MAX_LENGTH`를 200으로, DB `idempotency_key`도 `VARCHAR2(200)`으로 확장한다.
클라이언트가 보내는 `Idempotency-Key` 헤더 계약은 바뀌지 않는다 (prefix는 서버가 붙인다).

- 장점: 가짜 데이터 없음. `member_id IS NULL`이 "주인 없음"을 정확히 표현. `member` 테이블과
  인증 열거형을 오염시키지 않음. 회원 집계에 예외 규칙이 생기지 않음. 멱등키가 opaque해져
  범용 큐가 도메인 개념(`member:`)을 해석하지 않아도 됨. `reference_id` nullable과 패턴 일관.
- 단점: 스키마·코드 변경 필요. 저장된 키가 합성 문자열이라 눈으로 읽을 때 분해 필요.
  nullable FK 하나 증가.
- **관행 근거가 A보다 약하다.** flat 네임스페이싱 dedup 키 자체는 널리 쓰이지만
  (Temporal workflow ID, SQS `MessageDeduplicationId`, pg-boss `singletonKey`,
  river `unique_key`, sidekiq-unique-jobs 다이제스트), **그 시스템들은 대부분 owner 컬럼이 없다.**
  따라서 "owner 컬럼이 있는데 키에 접어넣는다"는 조합은 검증된 표준이 아니라 합성이다.

### 기각 사유 정리

B의 실질적 장점은 하나였다 — A의 마법 행 단점(집계에서 조용히 누락)을 피한다는 것.
그러나 그 단점은 위 체크리스트 3번(`isHuman()`으로 제외 로직 일원화)으로 관리 가능하고,
B는 관행적 지지가 없는 중간 형태이며 `member_id`를 nullable로 만드는 대가로
멱등 유니크 키 변경을 강제한다. A를 택한다.

**주의: `AuthSocialProvider.AI_PERSONA`를 선례로 쓸 수 없다.** 실제 운영 DB에 사용된 적이 없고
`AdminAnswerPostService.java:39`의 필터만 남아 있다. 즉 시스템 계정을 도입하면
"사람 아닌 member 행을 집계에서 제외하는 규율"을 **처음부터 세우는 것**이며,
기존 규율에 얹는 것이 아니다. 체크리스트 3번이 그래서 중요하다.

## `reference_type` 추가 트리거

지금은 `job_type`이 referent 타입을 100% 결정하므로 추가하지 않는다.
나중에 추가하는 비용도 낮다 (nullable 컬럼 추가 + `job_type`에서 백필, 계약 무변경).

**추가해야 하는 신호: 하나의 `job_type`이 두 종류 이상의 엔티티를 가리켜야 할 때.**
예) `SEND_PUSH` job이 상황에 따라 `daily_question_answer` 또는 `answer_post`를 가리켜야 하는 경우
— 이때는 `job_type`만으로 referent 타입을 알 수 없어 `reference_type`이 필수가 된다.

**트리거가 아닌 것:** `ANALYSIS_REPORT`와 `ANALYSIS_REPORT_REGENERATE`처럼 서로 다른 job_type이
같은 엔티티 타입을 가리키는 경우 — `job_type IN (...)`으로 충분히 조회된다.

추가 시 `reference_type`과 `reference_id`는 항상 함께 NULL이거나 함께 값이 있어야 하므로
`CHECK ((reference_type IS NULL) = (reference_id IS NULL))`이 필요하고,
`uk_background_job_reference`를 `(reference_type, reference_id)`로 옮길지 재검토해야 한다
(옮기면 job_type이 다른 재분석 job이 차단된다).

## 테스트

- `CreateAnalysisReportIntegrateTest` — 생성 순서, `reference_id` 값, `payload = "{}"`, 응답 필드
- `PublishAnalysisReportJobIntegrateTest` — 발행 성공/소진, `onPublishExhausted` 경로
- `BackgroundJobPublishProcessorIntegrateTest`, `RecoverExpiredBackgroundJobIntegrateTest` — rename 반영
- `TestBackgroundJobUtils` — 팩토리 시그니처
- **신규** — 같은 `(job_type, reference_id)`로 job 2건 저장 시 유니크 위반
- **신규** — `reference_id = NULL`인 같은 job_type 2건은 저장 성공 (partial 성질 확인)

## 문서 갱신 대상

- `erd.dbml` — `background_job`에 `reference_id`·`payload`, 인덱스 추가;
  `analysis_report`에서 `background_job_id` 및 `Ref:` 제거
- `src/main/java/site/one_question/api/backgroundjob/CLAUDE.md` + `AGENTS.md`
  — `job_data` → `payload` 및 역할 정의, `reference_id` 불변식, `reference_type` 트리거,
  `member_id`는 NOT NULL 유지 + 시스템 job은 시스템 계정 방식이라는 방향과 체크리스트
- `src/main/java/site/one_question/api/analysisreport/CLAUDE.md` + `AGENTS.md`
  — job_data 내용, `background_job_id` 역조회, 생성 순서 관련 줄
- `BACKGROUND_JOB_ARCHITECTURE.md`, `README.md`

## 구현 중 검증할 것

Oracle dev DB에 `(job_type, reference_id) = ('X', NULL)` 2행을 실제로 넣어
평범한 유니크 인덱스가 위반을 내는지 확인한다. 위반이 나지 않으면 CASE 트릭 없이
평범한 유니크로 단순화한다.
