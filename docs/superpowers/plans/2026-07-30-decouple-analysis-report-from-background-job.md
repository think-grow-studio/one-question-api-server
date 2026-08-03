# analysis_report → background_job 의존성 제거 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `analysis_report`가 `background_job`을 FK로 참조하는 역전을 없애고, `background_job.reference_id`가 대상 애그리거트를 가리키는 커맨드 큐 구조로 바꾼다.

**Architecture:** `background_job`에 nullable `reference_id NUMBER`(FK 없는 폴리모픽 참조)를 두고 `job_type`이 대상 타입을 결정한다. `analysis_report.background_job_id`는 컬럼·FK·유니크 제약과 함께 삭제한다. 생성 순서는 report → job으로 뒤집힌다. `job_data`는 `payload`로 rename하고 "도메인 행에 없는 커맨드 파라미터"로 역할을 좁힌다 — ANALYSIS_REPORT는 `{}`.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Oracle(prod/dev/local), H2 MySQL mode(test), JUnit 5 + AssertJ + MockMvc

## Global Constraints

- 스키마 변경은 `ddl-auto`에 의존하지 않는다. prod/dev/local은 `spring.jpa.hibernate.ddl-auto=validate`(Oracle).
- **마이그레이션은 `src/main/resources/migration/13. create_analysis_report_job_tables.sql`을 직접 수정한다.** 14번 신설 금지. 운영 이력이 없으므로 백필 SQL도 작성하지 않는다. dev DB는 세 테이블(`analysis_report_source`, `analysis_report`, `background_job`)을 수동 DROP 후 재실행한다.
- 테스트 DB는 H2 `MODE=MySQL` + `ddl-auto=create-drop` (`src/test/resources/application-test.properties`). 마이그레이션 SQL은 테스트에서 실행되지 않으므로, 테스트가 보는 스키마는 **엔티티 애노테이션에서 생성된다.**
- Hibernate `validate`는 테이블/컬럼/타입만 검증하고 제약·인덱스는 보지 않는다. 그래서 엔티티의 `@UniqueConstraint(columnNames = {"job_type", "reference_id"})`와 Oracle 실제 인덱스(CASE 표현식)의 형태 차이가 부팅을 깨지 않는다. **이 사실에 의존하는 설계이므로 마이그레이션에 주석으로 남긴다.**
- `reference_id` 타입은 `NUMBER` / Java `Long`. String 금지 (Oracle 암묵 형변환이 인덱스를 못 타게 함).
- `payload`는 `NOT NULL` 유지. 빈 값은 `"{}"`. nullable 금지.
- `member_id`는 `NOT NULL` FK 유지. 멱등 유니크 키 `(member_id, job_type, idempotency_key)`도 그대로. **이번 작업에서 건드리지 않는다.**
- `request_hash`와 `BackgroundJobMessage(jobId, correlationId)`는 변경하지 않는다.
- 프로젝트 컨벤션: DTO는 record, 엔티티 생성은 정적 팩토리 `create()`/`createPending()`, DI는 `@RequiredArgsConstructor`, 예외는 해당 컨텍스트 `*ExceptionSpec`에 코드 추가 후 `*Exception` 하위 클래스.
- 테스트 컨벤션(`src/test/java/site/one_question/CLAUDE.md`): 통합 테스트는 `integrate/{도메인}/`에 `IntegrateTest` 상속. 데이터 생성은 `test_config/utils/TestXxxUtils`로만. 메서드명 `{동작}_{조건}_then_{결과}`, `@DisplayName`은 한국어. TestUtils 명명은 `createSave()` / `createSave_With_{설명}(...)`.
- 빌드/테스트: `./gradlew build` / `./gradlew test`

## 스펙에서 의도적으로 벗어난 결정

스펙의 "`onPublishExhausted(Long jobId, …)` → `ClaimedBackgroundJob`을 받아 재조회 없이 `referenceId` 사용"은 **채택하지 않는다.**

`BackgroundJobPublishApplication.java:96`의 만료 claim 복구 경로가 `ExpiredPublishClaim`(referenceId 없음)으로 같은 콜백을 호출한다. 시그니처를 바꾸면 `PendingPublishTarget`·`ExpiredPublishClaim`·`ClaimedBackgroundJob` 세 projection과 두 JPQL `SELECT new ...`에 모두 referenceId를 끼워야 한다. 재시도 소진은 5회 실패 후에만 도달하는 드문 경로이므로, `BackgroundJobService.findById(jobId)` 한 번이 낫다. 기존 시그니처를 유지한다.

## File Structure

| 파일 | 책임 | 변경 |
|---|---|---|
| `src/main/resources/migration/13. create_analysis_report_job_tables.sql` | Oracle 스키마 원천 | 수정 |
| `api/backgroundjob/domain/BackgroundJob.java` | job 애그리거트. `referenceId`·`payload` 보유 | 수정 |
| `api/backgroundjob/domain/ClaimedBackgroundJob.java` | 선점된 job 스냅샷 | 수정 |
| `api/backgroundjob/domain/BackgroundJobService.java` | job 조회/CAS 파사드 | `findById` 추가 |
| `api/analysisreport/domain/AnalysisReport.java` | 리포트 애그리거트 | `backgroundJob` 제거 |
| `api/analysisreport/domain/AnalysisReportRepository.java` | 리포트 저장소 | job 조회 메서드 제거 |
| `api/analysisreport/domain/AnalysisReportService.java` | 리포트 조회 파사드 | `findById`로 교체 |
| `api/analysisreport/application/AnalysisReportApplication.java` | 생성 유스케이스 | 순서 역전 |
| `api/analysisreport/application/AnalysisReportJobPublisher.java` | ANALYSIS_REPORT 발행자 | referenceId 경로 |
| `test_config/utils/TestBackgroundJobUtils.java` | job 테스트 픽스처 | 시그니처 |
| `test_config/utils/TestAnalysisReportUtils.java` | 리포트 테스트 픽스처 | 시그니처 |
| `integrate/analysisreport/CreateAnalysisReportIntegrateTest.java` | 생성 API 검증 | 단정 갱신 |
| `integrate/analysisreport/PublishAnalysisReportJobIntegrateTest.java` | 발행 검증 | setup 순서 |
| `integrate/backgroundjob/BackgroundJobReferenceUniqueIntegrateTest.java` | 1:1 제약 검증 | **신규** |
| `erd.dbml`, 두 컨텍스트 `CLAUDE.md`+`AGENTS.md`, `BACKGROUND_JOB_ARCHITECTURE.md` | 문서 | 수정 (6개) |

---

### Task 1: `job_data` → `payload` 순수 rename

동작 변화 없는 기계적 rename. 기존 테스트가 그대로 통과해야 한다.

**Files:**
- Modify: `src/main/resources/migration/13. create_analysis_report_job_tables.sql:8`
- Modify: `src/main/java/site/one_question/api/backgroundjob/domain/BackgroundJob.java:50-52,106-133`
- Modify: `src/main/java/site/one_question/api/backgroundjob/domain/ClaimedBackgroundJob.java`
- Modify: `src/test/java/site/one_question/integrate/test_config/utils/TestBackgroundJobUtils.java:26-40`
- Test: `src/test/java/site/one_question/integrate/analysisreport/CreateAnalysisReportIntegrateTest.java:115`
- Test: `src/test/java/site/one_question/integrate/analysisreport/PublishAnalysisReportJobIntegrateTest.java:39-40`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: `BackgroundJob.getPayload(): String`, `BackgroundJob.create(BackgroundJobType, Member, String payload, String correlationId, IdempotencyKey, RequestHash): BackgroundJob`, `ClaimedBackgroundJob.payload(): String`, `TestBackgroundJobUtils.createSave_With_Payload(Member, String): BackgroundJob`

- [ ] **Step 1: 마이그레이션 13번의 컬럼명 변경**

`src/main/resources/migration/13. create_analysis_report_job_tables.sql`의 8번 줄:

```sql
    payload         CLOB                      NOT NULL,
```

- [ ] **Step 2: `BackgroundJob` 필드 rename**

`BackgroundJob.java`의 50-52번 줄을 아래로 교체한다.

```java
    /**
     * 이 커맨드의 파라미터 JSON. 도메인 행에 존재하지 않는 값만 담는다.
     * 담을 파라미터가 없으면 빈 객체 {@code "{}"}를 저장한다 — NULL 을 쓰지 않는 이유는
     * 소비자(Python 워커 포함)에 null 분기가 영구히 생기는 것을 막기 위함이다.
     * 생성 시점에 확정되는 불변 값이며 이후 갱신하지 않는다.
     */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;
```

`create()`의 파라미터명과 전달 인자도 `jobData` → `payload`로 바꾼다 (106-133번 줄). 필드 선언 순서가 `@AllArgsConstructor` 인자 순서이므로 **위치는 그대로 유지**한다.

```java
    public static BackgroundJob create(
            BackgroundJobType jobType,
            Member member,
            String payload,
            String correlationId,
            IdempotencyKey idempotencyKey,
            RequestHash requestHash
    ) {
        return new BackgroundJob(
                null,
                jobType,
                member,
                payload,
                correlationId,
                idempotencyKey.value(),
                requestHash.value(),
                BackgroundJobStatus.PENDING,
                null,
                null,
                Instant.now(),
                null,
                null,
                null,
                0,
                null,
                null
        );
    }
```

- [ ] **Step 3: `ClaimedBackgroundJob` rename**

```java
package site.one_question.api.backgroundjob.domain;

public record ClaimedBackgroundJob(
        Long id,
        BackgroundJobType jobType,
        String payload,
        String correlationId,
        String claimId,
        int retryCount
) {
    public static ClaimedBackgroundJob from(BackgroundJob job) {
        return new ClaimedBackgroundJob(
                job.getId(),
                job.getJobType(),
                job.getPayload(),
                job.getCorrelationId(),
                job.getPublishClaimId(),
                job.getRetryCount()
        );
    }
}
```

- [ ] **Step 4: `AnalysisReportApplication`의 지역 변수명 정리**

`AnalysisReportApplication.java:87-92`의 `createJobData`를 `createPayload`로 rename한다. 내용은 그대로 (다음 태스크에서 삭제된다).

```java
    private String createPayload(Long memberId, AnalysisReportType reportType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", memberId);
        payload.put("reportType", reportType.name());
        return toJson(payload);
    }
```

74번 줄의 호출도 `createPayload(memberId, reportType)`으로 바꾼다.

- [ ] **Step 5: `TestBackgroundJobUtils` rename**

26-40번 줄을 아래로 교체한다.

```java
    public BackgroundJob createSave(Member member) {
        return createSave_With_Payload(member, "{}");
    }

    public BackgroundJob createSave_With_Payload(Member member, String payload) {
        BackgroundJob job = BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                payload,
                UUID.randomUUID().toString(),
                new IdempotencyKey(UUID.randomUUID().toString()),
                RequestHash.sha256(payload)
        );
        return repository.save(job);
    }
```

- [ ] **Step 6: 테스트의 호출부 rename**

`CreateAnalysisReportIntegrateTest.java:115`:

```java
            Map<String, Object> payload = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
```

116, 119, 122번 줄의 `jobData` 변수 참조도 `payload`로 바꾼다. 단정 메시지의 "job_data"도 "payload"로 바꾼다.

`PublishAnalysisReportJobIntegrateTest.java:39-40`:

```java
        job = testBackgroundJobUtils.createSave_With_Payload(
                member, "{\"memberId\":" + member.getId() + ",\"reportType\":\"THINKING_PATTERN\"}");
```

- [ ] **Step 7: 전체 테스트 실행 — rename이 동작을 바꾸지 않았음을 확인**

```bash
./gradlew test --tests '*AnalysisReport*' --tests '*BackgroundJob*'
```

기대: PASS. rename만 했으므로 단정 하나도 실패하지 않아야 한다.

- [ ] **Step 8: 커밋**

```bash
git add "src/main/resources/migration/13. create_analysis_report_job_tables.sql" \
  src/main/java/site/one_question/api/backgroundjob/domain/BackgroundJob.java \
  src/main/java/site/one_question/api/backgroundjob/domain/ClaimedBackgroundJob.java \
  src/main/java/site/one_question/api/analysisreport/application/AnalysisReportApplication.java \
  src/test/java/site/one_question/integrate/test_config/utils/TestBackgroundJobUtils.java \
  src/test/java/site/one_question/integrate/analysisreport/CreateAnalysisReportIntegrateTest.java \
  src/test/java/site/one_question/integrate/analysisreport/PublishAnalysisReportJobIntegrateTest.java
git commit -m "refactor(backgroundjob): job_data를 payload로 rename"
```

---

### Task 2: `reference_id` 컬럼과 1:1 유니크 제약 추가

컬럼과 제약만 먼저 도입한다. 이 태스크가 끝난 시점에 ANALYSIS_REPORT job은 아직 `reference_id = NULL`이다 — 컬럼이 nullable이고 "대상 애그리거트가 없는 job은 NULL"이 정당한 상태이므로 반쪽 상태가 아니다.

**Files:**
- Modify: `src/main/resources/migration/13. create_analysis_report_job_tables.sql`
- Modify: `src/main/java/site/one_question/api/backgroundjob/domain/BackgroundJob.java`
- Modify: `src/main/java/site/one_question/api/backgroundjob/domain/ClaimedBackgroundJob.java`
- Modify: `src/test/java/site/one_question/integrate/test_config/utils/TestBackgroundJobUtils.java`
- Modify: `src/main/java/site/one_question/api/analysisreport/application/AnalysisReportApplication.java:71-78`
- Modify: `src/test/java/site/one_question/integrate/analysisreport/PublishAnalysisReportJobIntegrateTest.java:39-40`
- Test: `src/test/java/site/one_question/integrate/backgroundjob/BackgroundJobReferenceUniqueIntegrateTest.java` (신규)

**Interfaces:**
- Consumes: Task 1의 `BackgroundJob.create(BackgroundJobType, Member, String payload, String, IdempotencyKey, RequestHash)`, `TestBackgroundJobUtils.createSave_With_Payload(Member, String)`
- Produces: `BackgroundJob.getReferenceId(): Long`, `BackgroundJob.create(BackgroundJobType, Member, Long referenceId, String payload, String correlationId, IdempotencyKey, RequestHash): BackgroundJob`, `ClaimedBackgroundJob.referenceId(): Long`, `TestBackgroundJobUtils.createSave_With_Reference(Member, Long): BackgroundJob`, `TestBackgroundJobUtils.createSave(Member): BackgroundJob`(referenceId=null)

- [ ] **Step 1: 실패하는 테스트 작성 — 1:1 제약**

`src/test/java/site/one_question/integrate/backgroundjob/BackgroundJobReferenceUniqueIntegrateTest.java` 신규 생성:

```java
package site.one_question.integrate.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("BackgroundJob reference_id 유니크 제약 통합 테스트")
class BackgroundJobReferenceUniqueIntegrateTest extends IntegrateTest {

    @Test
    @DisplayName("같은 job_type과 reference_id로 두 번 저장하면 유니크 제약을 위반한다")
    void save_job_with_duplicated_reference_then_violates_unique_constraint() {
        // given
        Member member = testMemberUtils.createSave();
        Long referenceId = 4242L;
        testBackgroundJobUtils.createSave_With_Reference(member, referenceId);

        // when & then
        assertThatThrownBy(() ->
                testBackgroundJobUtils.createSave_With_Reference(member, referenceId))
                .as("대상 애그리거트 1건당 job 1건이어야 함")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(backgroundJobRepository.findAll())
                .as("제약 위반 시 두 번째 job이 저장되지 않아야 함")
                .hasSize(1);
    }

    @Test
    @DisplayName("reference_id가 NULL인 job은 같은 job_type으로 여러 건 저장할 수 있다")
    void save_multiple_jobs_with_null_reference_then_succeeds() {
        // given
        Member member = testMemberUtils.createSave();

        // when
        testBackgroundJobUtils.createSave(member);
        testBackgroundJobUtils.createSave(member);

        // then
        assertThat(backgroundJobRepository.findAll())
                .as("대상 애그리거트가 없는 job은 NULL 참조로 여러 건 존재할 수 있어야 함")
                .hasSize(2);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

```bash
./gradlew test --tests '*BackgroundJobReferenceUniqueIntegrateTest*'
```

기대: 컴파일 에러 — `createSave_With_Reference` 심볼을 찾을 수 없음.

- [ ] **Step 3: 마이그레이션에 컬럼·주석·인덱스 추가**

`background_job` CREATE TABLE의 `member_id` 다음 줄에 컬럼을 추가한다.

```sql
    member_id       NUMBER                    NOT NULL,
    reference_id    NUMBER,
    payload         CLOB                      NOT NULL,
```

CREATE TABLE 문 뒤, 기존 `CREATE INDEX idx_background_job_status_scheduled` 위에 다음을 추가한다.

```sql
COMMENT ON COLUMN background_job.reference_id IS
    'job_type이 가리키는 도메인 애그리거트의 id. 폴리모픽 참조이므로 FK 없음. 대상이 없는 job 타입은 NULL.';

-- 대상 애그리거트 1건당 job 1건.
-- reference_id 가 NULL 이면 두 표현식이 모두 NULL 이 되어 인덱스에서 제외된다
-- (Oracle 에서 partial unique index 를 만드는 관용구).
-- 엔티티에는 @UniqueConstraint(columnNames = {"job_type", "reference_id"}) 로 선언돼 있다.
-- 형태가 다른데도 부팅이 깨지지 않는 이유는 Hibernate ddl-auto=validate 가
-- 테이블/컬럼/타입만 검증하고 제약·인덱스는 보지 않기 때문이다.
-- PostgreSQL 로 이전하면 아래로 단순화할 수 있다:
--   CREATE UNIQUE INDEX uk_background_job_reference
--       ON background_job (job_type, reference_id) WHERE reference_id IS NOT NULL;
CREATE UNIQUE INDEX uk_background_job_reference ON background_job
    (CASE WHEN reference_id IS NULL THEN NULL ELSE job_type END, reference_id);
```

- [ ] **Step 4: `BackgroundJob`에 필드·제약 추가**

`@Table` 애노테이션(29-35번 줄)을 아래로 교체한다.

```java
@Table(
        name = "background_job",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_background_job_idempotency",
                        columnNames = {"member_id", "job_type", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_background_job_reference",
                        columnNames = {"job_type", "reference_id"}
                )
        }
)
```

`member` 필드 선언 바로 다음에 `referenceId`를 추가한다. **필드 순서가 `@AllArgsConstructor` 인자 순서이므로 위치가 중요하다.**

```java
    /**
     * 이 커맨드가 대상으로 하는 도메인 애그리거트의 id.
     * 대상 타입은 {@code job_type} 이 결정하는 폴리모픽 참조이므로 FK 를 걸지 않는다.
     * 대상 애그리거트가 없는 job 타입(배치·집계 등)은 NULL 이다.
     * 생성 시점에 확정되는 불변 값이며 이후 갱신하지 않는다.
     */
    @Column(name = "reference_id")
    private Long referenceId;
```

`create()`에 `referenceId` 파라미터를 `member` 다음에 추가하고, 생성자 인자도 같은 위치에 넣는다.

```java
    public static BackgroundJob create(
            BackgroundJobType jobType,
            Member member,
            Long referenceId,
            String payload,
            String correlationId,
            IdempotencyKey idempotencyKey,
            RequestHash requestHash
    ) {
        return new BackgroundJob(
                null,
                jobType,
                member,
                referenceId,
                payload,
                correlationId,
                idempotencyKey.value(),
                requestHash.value(),
                BackgroundJobStatus.PENDING,
                null,
                null,
                Instant.now(),
                null,
                null,
                null,
                0,
                null,
                null
        );
    }
```

- [ ] **Step 5: `ClaimedBackgroundJob`에 referenceId 추가**

```java
package site.one_question.api.backgroundjob.domain;

public record ClaimedBackgroundJob(
        Long id,
        BackgroundJobType jobType,
        Long referenceId,
        String payload,
        String correlationId,
        String claimId,
        int retryCount
) {
    public static ClaimedBackgroundJob from(BackgroundJob job) {
        return new ClaimedBackgroundJob(
                job.getId(),
                job.getJobType(),
                job.getReferenceId(),
                job.getPayload(),
                job.getCorrelationId(),
                job.getPublishClaimId(),
                job.getRetryCount()
        );
    }
}
```

- [ ] **Step 6: `TestBackgroundJobUtils`에 referenceId 팩토리 추가**

Task 1에서 만든 22-40번 줄 영역을 아래로 교체한다.

```java
    public BackgroundJob createSave() {
        return createSave(testMemberUtils.createSave());
    }

    public BackgroundJob createSave(Member member) {
        return createSave_With_Reference(member, null);
    }

    public BackgroundJob createSave_With_Reference(Member member, Long referenceId) {
        BackgroundJob job = BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                referenceId,
                "{}",
                UUID.randomUUID().toString(),
                new IdempotencyKey(UUID.randomUUID().toString()),
                RequestHash.sha256("{}")
        );
        return repository.save(job);
    }
```

`createSave_With_Payload`는 삭제한다 — payload는 이제 항상 `"{}"`이고, 유일한 호출자였던 `PublishAnalysisReportJobIntegrateTest`는 Step 7에서 `createSave_With_Reference`로 바꾼다.

- [ ] **Step 7: 기존 호출부를 새 시그니처에 맞춘다**

`AnalysisReportApplication.java`의 job 생성부(71-78번 줄)에 `null`을 넣는다. 실제 리포트 id는 Task 3에서 채운다.

```java
        BackgroundJob backgroundJob = backgroundJobService.save(BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                null,
                createPayload(memberId, reportType),
                resolveCorrelationId(),
                idempotencyKey,
                requestHash
        ));
```

`PublishAnalysisReportJobIntegrateTest.java:39-40`:

```java
        job = testBackgroundJobUtils.createSave(member);
```

- [ ] **Step 8: 테스트 통과 확인**

```bash
./gradlew test --tests '*BackgroundJobReferenceUniqueIntegrateTest*'
```

기대: 두 테스트 모두 PASS. 두 번째 테스트가 실패하면 H2가 NULL을 서로 같다고 본다는 뜻이므로, `application-test.properties`의 `MODE=MySQL`이 유지되고 있는지 확인한다.

- [ ] **Step 9: 전체 테스트 실행**

```bash
./gradlew test
```

기대: PASS.

- [ ] **Step 10: 커밋**

```bash
git add "src/main/resources/migration/13. create_analysis_report_job_tables.sql" \
  src/main/java/site/one_question/api/backgroundjob/domain/BackgroundJob.java \
  src/main/java/site/one_question/api/backgroundjob/domain/ClaimedBackgroundJob.java \
  src/main/java/site/one_question/api/analysisreport/application/AnalysisReportApplication.java \
  src/test/java/site/one_question/integrate/test_config/utils/TestBackgroundJobUtils.java \
  src/test/java/site/one_question/integrate/analysisreport/PublishAnalysisReportJobIntegrateTest.java \
  src/test/java/site/one_question/integrate/backgroundjob/BackgroundJobReferenceUniqueIntegrateTest.java
git commit -m "feat(backgroundjob): reference_id 컬럼과 대상별 1:1 유니크 제약 추가"
```

---

### Task 3: 의존성 역전 — `analysis_report.background_job_id` 제거

`analysis_report`에서 job 역참조를 없애고, 생성 순서를 report → job으로 뒤집어 `reference_id`에 실제 리포트 id를 채운다.

**Files:**
- Modify: `src/main/resources/migration/13. create_analysis_report_job_tables.sql:36-55`
- Modify: `src/main/java/site/one_question/api/analysisreport/domain/AnalysisReport.java`
- Modify: `src/main/java/site/one_question/api/analysisreport/domain/AnalysisReportRepository.java`
- Modify: `src/main/java/site/one_question/api/analysisreport/domain/AnalysisReportService.java`
- Modify: `src/main/java/site/one_question/api/analysisreport/application/AnalysisReportApplication.java`
- Modify: `src/main/java/site/one_question/api/analysisreport/application/AnalysisReportJobPublisher.java`
- Modify: `src/main/java/site/one_question/api/backgroundjob/domain/BackgroundJobService.java`
- Modify: `src/test/java/site/one_question/integrate/test_config/utils/TestAnalysisReportUtils.java`
- Test: `src/test/java/site/one_question/integrate/analysisreport/CreateAnalysisReportIntegrateTest.java`
- Test: `src/test/java/site/one_question/integrate/analysisreport/PublishAnalysisReportJobIntegrateTest.java`
- Test: `src/test/java/site/one_question/integrate/backgroundjob/BackgroundJobReferenceUniqueIntegrateTest.java`

**Interfaces:**
- Consumes: Task 2의 `BackgroundJob.create(BackgroundJobType, Member, Long referenceId, String payload, String, IdempotencyKey, RequestHash)`, `BackgroundJob.getReferenceId()`, `TestBackgroundJobUtils.createSave_With_Reference(Member, Long)`
- Produces: `AnalysisReport.createPending(Member, AnalysisReportType): AnalysisReport`, `AnalysisReportService.findById(Long): AnalysisReport`, `BackgroundJobService.findById(Long): BackgroundJob`, `TestAnalysisReportUtils.createSave(Member): AnalysisReport`

- [ ] **Step 1: 실패하는 테스트로 바꾼다 — 생성 API 단정**

`CreateAnalysisReportIntegrateTest.java`의 115-124번 줄(payload/jobData 단정 블록)을 아래로 교체한다.

```java
            assertThat(job.getPayload())
                    .as("ANALYSIS_REPORT는 도메인 행 밖의 커맨드 파라미터가 없으므로 payload는 빈 객체여야 함")
                    .isEqualTo("{}");
            assertThat(job.getReferenceId())
                    .as("job은 reference_id로 대상 리포트를 가리켜야 함")
                    .isEqualTo(report.getId());
```

이제 쓰이지 않는 것들을 삭제한다: `asLong` private 메서드(417-419번 줄), `import com.fasterxml.jackson.core.type.TypeReference;`(8번 줄), `import java.util.Map;`(12번 줄).

- [ ] **Step 2: 실패하는 테스트로 바꾼다 — 발행 테스트 setup**

`PublishAnalysisReportJobIntegrateTest.java:36-42`를 아래로 교체한다. report가 먼저 만들어지고 job이 그것을 가리킨다.

```java
    @BeforeEach
    void setup() {
        member = testMemberUtils.createSave();
        report = testAnalysisReportUtils.createSave(member);
        job = testBackgroundJobUtils.createSave_With_Reference(member, report.getId());
    }
```

- [ ] **Step 3: 실패하는 테스트로 바꾼다 — 유니크 제약 테스트를 실제 리포트로**

`BackgroundJobReferenceUniqueIntegrateTest.java`의 첫 테스트에서 임의값 `4242L` 대신 실제 리포트를 쓴다.

```java
    @Test
    @DisplayName("같은 job_type과 reference_id로 두 번 저장하면 유니크 제약을 위반한다")
    void save_job_with_duplicated_reference_then_violates_unique_constraint() {
        // given
        Member member = testMemberUtils.createSave();
        AnalysisReport report = testAnalysisReportUtils.createSave(member);
        testBackgroundJobUtils.createSave_With_Reference(member, report.getId());

        // when & then
        assertThatThrownBy(() ->
                testBackgroundJobUtils.createSave_With_Reference(member, report.getId()))
                .as("리포트 1건당 job 1건이어야 함")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(backgroundJobRepository.findAll())
                .as("제약 위반 시 두 번째 job이 저장되지 않아야 함")
                .hasSize(1);
    }
```

`import site.one_question.api.analysisreport.domain.AnalysisReport;`를 추가한다.

- [ ] **Step 4: 컴파일/테스트 실패 확인**

```bash
./gradlew test --tests '*AnalysisReport*' --tests '*BackgroundJobReference*'
```

기대: 컴파일 에러 — `testAnalysisReportUtils.createSave(Member)` 심볼 없음(현재는 `createSave(BackgroundJob, Member)`).

- [ ] **Step 5: `AnalysisReport`에서 `backgroundJob` 제거**

`AnalysisReport.java`의 37-39번 줄(`@OneToOne` 필드)을 삭제하고, `createPending`을 아래로 바꾼다. 필드 개수가 9 → 8이 되므로 생성자 인자도 8개다.

```java
    public static AnalysisReport createPending(
            Member member,
            AnalysisReportType reportType
    ) {
        return new AnalysisReport(
                null,
                member,
                reportType,
                AnalysisReportStatus.PENDING,
                null,
                null,
                null,
                null
        );
    }
```

이제 쓰이지 않는 import를 삭제한다: `jakarta.persistence.OneToOne`, `site.one_question.api.backgroundjob.domain.BackgroundJob`.

- [ ] **Step 6: `AnalysisReportRepository`에서 job 조회 제거**

```java
package site.one_question.api.analysisreport.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
}
```

- [ ] **Step 7: `AnalysisReportService`를 `findById`로 교체**

```java
package site.one_question.api.analysisreport.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {

    private final AnalysisReportRepository analysisReportRepository;

    public AnalysisReport save(AnalysisReport analysisReport) {
        return analysisReportRepository.save(analysisReport);
    }

    public AnalysisReport findById(Long id) {
        return analysisReportRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "analysis report not found: " + id));
    }
}
```

- [ ] **Step 8: `BackgroundJobService`에 `findById` 추가**

`save()` 다음에 추가한다.

```java
    public BackgroundJob findById(Long jobId) {
        return backgroundJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException(
                        "background job not found: " + jobId));
    }
```

- [ ] **Step 9: `AnalysisReportApplication` 생성 순서 역전**

`create()`의 멱등 재요청 경로(56-63번 줄)에서 `findByBackgroundJob`을 `findById`로 바꾼다.

```java
        var existingJob = backgroundJobService.findByIdempotencyKey(
                memberId, BackgroundJobType.ANALYSIS_REPORT, idempotencyKey);
        if (existingJob.isPresent()) {
            BackgroundJob backgroundJob = existingJob.get();
            backgroundJob.validateSameRequestHash(requestHash);
            AnalysisReport analysisReport =
                    analysisReportService.findById(backgroundJob.getReferenceId());
            return CreateAnalysisReportResponse.from(backgroundJob, analysisReport);
        }
```

생성부(71-82번 줄)를 report 우선으로 바꾼다.

```java
        // report 를 먼저 만들어 id 를 확보한다. IDENTITY 전략이므로 save() 시점에 flush 되어
        // 같은 트랜잭션 안에서 즉시 id 를 읽을 수 있다.
        AnalysisReport analysisReport = analysisReportService.save(
                AnalysisReport.createPending(member, reportType));

        BackgroundJob backgroundJob = backgroundJobService.save(BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                analysisReport.getId(),
                EMPTY_PAYLOAD,
                resolveCorrelationId(),
                idempotencyKey,
                requestHash
        ));

        analysisReportSourceService.createAll(analysisReport, memberId, sourceAnswers);
```

클래스 상단 필드 선언부에 상수를 추가한다.

```java
    /**
     * ANALYSIS_REPORT 커맨드는 도메인 행 밖의 파라미터가 없다.
     * memberId 는 background_job.member_id, reportId 는 reference_id,
     * reportType 은 analysis_report.report_type 이 원천이다.
     */
    private static final String EMPTY_PAYLOAD = "{}";
```

Task 1에서 rename한 `createPayload` 메서드를 삭제한다. `toJson`과 `objectMapper`는 `createRequestHash`가 계속 쓰므로 남긴다. 삭제 후 쓰이지 않는 import가 있으면 함께 정리한다 (`LinkedHashMap`, `Map`은 `createRequestHash`가 쓰므로 유지).

- [ ] **Step 10: `AnalysisReportJobPublisher`가 referenceId로 리포트를 찾도록 변경**

```java
package site.one_question.api.analysisreport.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import site.one_question.api.analysisreport.domain.AnalysisReportJobQueueGateway;
import site.one_question.api.analysisreport.domain.AnalysisReportService;
import site.one_question.api.backgroundjob.domain.BackgroundJobMessage;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobService;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.ClaimedBackgroundJob;

@Component
public class AnalysisReportJobPublisher implements BackgroundJobPublisher {

    private final AnalysisReportService analysisReportService;
    private final BackgroundJobService backgroundJobService;
    private final AnalysisReportJobQueueGateway queueGateway;
    private final TransactionTemplate requiresNew;

    public AnalysisReportJobPublisher(
            AnalysisReportService analysisReportService,
            BackgroundJobService backgroundJobService,
            AnalysisReportJobQueueGateway queueGateway,
            PlatformTransactionManager transactionManager
    ) {
        this.analysisReportService = analysisReportService;
        this.backgroundJobService = backgroundJobService;
        this.queueGateway = queueGateway;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public BackgroundJobType supportedType() {
        return BackgroundJobType.ANALYSIS_REPORT;
    }

    @Override
    public void publish(ClaimedBackgroundJob job) {
        queueGateway.send(BackgroundJobMessage.from(job));
    }

    /**
     * 발행 재시도 소진 시 리포트도 FAILED 로 동기화한다.
     * 만료 claim 복구 경로({@code ExpiredPublishClaim})도 같은 콜백을 쓰므로 jobId 만 받고,
     * 대상 리포트는 job 의 {@code reference_id} 로 찾는다. 5회 실패 후에만 도달하는 드문 경로라
     * 조회 한 번을 감수한다.
     */
    @Override
    public void onPublishExhausted(Long jobId, Exception cause) {
        requiresNew.executeWithoutResult(status -> {
            Long reportId = backgroundJobService.findById(jobId).getReferenceId();
            analysisReportService.findById(reportId).fail();
        });
    }
}
```

- [ ] **Step 11: `TestAnalysisReportUtils` 시그니처 변경**

```java
package site.one_question.integrate.test_config.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportRepository;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestAnalysisReportUtils {

    private final AnalysisReportRepository repository;

    public AnalysisReport createSave(Member member) {
        AnalysisReport report = AnalysisReport.createPending(
                member,
                AnalysisReportType.THINKING_PATTERN
        );
        return repository.save(report);
    }
}
```

- [ ] **Step 12: 마이그레이션에서 `analysis_report`의 job 역참조 제거**

`analysis_report` CREATE TABLE에서 `background_job_id NUMBER NOT NULL,` 줄, `CONSTRAINT fk_analysis_report_job ...` 줄, `CONSTRAINT uk_analysis_report_job UNIQUE (background_job_id)` 줄을 삭제한다. 결과:

```sql
CREATE TABLE analysis_report (
    id                NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    member_id         NUMBER                    NOT NULL,
    report_type       VARCHAR2(100)             NOT NULL,
    status            VARCHAR2(30)              NOT NULL,
    result            CLOB,
    provider          VARCHAR2(30),
    model             VARCHAR2(100),
    llm_options       CLOB,
    created_at        TIMESTAMP WITH TIME ZONE,
    created_by        NUMBER,
    updated_at        TIMESTAMP WITH TIME ZONE,
    updated_by        NUMBER,
    CONSTRAINT fk_analysis_report_member FOREIGN KEY (member_id) REFERENCES member(id)
);
```

- [ ] **Step 13: 테스트 통과 확인**

```bash
./gradlew test --tests '*AnalysisReport*' --tests '*BackgroundJob*'
```

기대: PASS. `CreateAnalysisReportIntegrateTest`의 `job.getReferenceId()`가 `report.getId()`와 같아야 하고, 멱등 재요청 테스트가 `reference_id` 경로로 기존 리포트를 찾아야 한다.

- [ ] **Step 14: 전체 빌드**

```bash
./gradlew build
```

기대: PASS.

- [ ] **Step 15: 커밋**

```bash
git add "src/main/resources/migration/13. create_analysis_report_job_tables.sql" \
  src/main/java/site/one_question/api/analysisreport/ \
  src/main/java/site/one_question/api/backgroundjob/domain/BackgroundJobService.java \
  src/test/java/site/one_question/integrate/
git commit -m "refactor(analysisreport): background_job 역참조 제거하고 reference_id로 역전"
```

---

### Task 4: 문서 갱신

코드와 문서가 어긋난 채로 남지 않게 같은 브랜치에서 정리한다.

**Files:**
- Modify: `erd.dbml:454-520`
- Modify: `src/main/java/site/one_question/api/backgroundjob/CLAUDE.md`
- Modify: `src/main/java/site/one_question/api/backgroundjob/AGENTS.md`
- Modify: `src/main/java/site/one_question/api/analysisreport/CLAUDE.md`
- Modify: `src/main/java/site/one_question/api/analysisreport/AGENTS.md`
- Modify: `BACKGROUND_JOB_ARCHITECTURE.md`
- Verify only: `README.md` (컬럼 구조를 서술하지 않으므로 변경 없음)

**Interfaces:**
- Consumes: Task 1~3의 최종 스키마와 클래스 구조
- Produces: 없음 (문서)

- [ ] **Step 1: `erd.dbml`의 `background_job` 갱신**

`member_id` 다음에 컬럼을 추가한다.

```
  reference_id        number        [note: 'job_type이 가리키는 도메인 애그리거트 id. 폴리모픽이라 FK 없음. 대상 없는 타입은 NULL']
  payload             clob          [not null, note: '커맨드 파라미터 JSON. 도메인 행에 없는 값만. 없으면 {}']
```

`job_data` 줄은 삭제한다. `indexes` 블록에 추가한다.

```
    (job_type, reference_id) [unique, name: 'uk_background_job_reference', note: 'Oracle 실제 인덱스는 reference_id IS NULL 행을 제외하는 함수 기반 partial unique']
```

- [ ] **Step 2: `erd.dbml`의 `analysis_report` 갱신**

`background_job_id number [not null, unique, ...]` 줄(495번)과 `Ref: background_job.id - analysis_report.background_job_id` 줄(519번)을 삭제한다.

- [ ] **Step 3: `backgroundjob/CLAUDE.md` 갱신**

`job_data` 관련 줄을 아래로 교체한다.

```markdown
- `payload`는 발행자·워커가 읽을 JSON 문자열이며 **도메인 행에 존재하지 않는 커맨드 파라미터만** 담는다. 담을 것이 없으면 빈 객체 `{}`를 저장한다(NULL 금지 — 소비자에 null 분기가 영구히 생기는 것을 막는다). 생성 시점에 확정되는 불변 값이다.
- `reference_id`는 이 커맨드가 대상으로 하는 도메인 애그리거트의 id다. 대상 타입은 `job_type`이 결정하는 폴리모픽 참조이므로 FK를 걸지 않는다. 대상 애그리거트가 없는 job 타입(배치·집계)은 NULL이다. 생성 후 갱신하지 않는다.
- `uk_background_job_reference`가 대상 애그리거트 1건당 job 1건을 강제한다. Oracle 실제 인덱스는 `(CASE WHEN reference_id IS NULL THEN NULL ELSE job_type END, reference_id)` 함수 기반이라 `reference_id IS NULL` 행을 제외한다. 엔티티에는 `(job_type, reference_id)` `@UniqueConstraint`로 선언돼 있고, 형태가 달라도 부팅이 깨지지 않는 이유는 `ddl-auto=validate`가 제약·인덱스를 검증하지 않기 때문이다.
- `reference_type` 컬럼은 두지 않는다. 현재 `job_type`이 대상 타입을 100% 결정한다. **추가해야 하는 신호는 하나의 `job_type`이 두 종류 이상의 엔티티를 가리켜야 할 때**다(예: `SEND_PUSH`가 `daily_question_answer` 또는 `answer_post`를 가리켜야 하는 경우). 서로 다른 job_type이 같은 엔티티 타입을 가리키는 것은 트리거가 아니다 — `job_type IN (...)`으로 조회된다.
- `member_id`는 NOT NULL FK로 유지한다. 주인 없는 시스템 job이 필요해지면 `member_id`를 nullable로 만들지 않고 **시스템 계정 행**을 만든다(`AuthSocialProvider`에 `SYSTEM` 추가 + 시드 1행 + `isHuman()` 헬퍼로 회원 집계 제외 로직 일원화). nullable로 만들면 `uk_background_job_idempotency`가 PostgreSQL/MySQL에서 조용히 무력화된다 — DB는 NULL끼리 같다고 보지 않으므로 `(NULL, 'X', 'key')` 두 행이 모두 저장된다. 참고: `AuthSocialProvider.AI_PERSONA`는 운영에 사용된 적이 없어 선례가 아니다.
```

- [ ] **Step 4: `analysisreport/CLAUDE.md` 갱신**

다음 줄들을 교체한다.

```markdown
- 리포트 생성 API는 한 트랜잭션에서 `analysis_report(PENDING)` → `background_job(PENDING)` → `analysis_report_source` 순으로 생성한다. 리포트를 먼저 만들어 id를 확보하고, 그 id를 `background_job.reference_id`에 넣는다. 리포트는 자기를 만든 job을 모른다.
- `background_job.payload`는 ANALYSIS_REPORT의 경우 빈 객체 `{}`다. memberId는 `background_job.member_id`, reportId는 `reference_id`, reportType은 `analysis_report.report_type`이 원천이므로 중복 저장하지 않는다. 소스 답변 목록도 `analysis_report_source`가 원천이다.
- SQS 메시지 body는 참조용으로 `jobId`, `correlationId`만 담는다(Claim Check 패턴, 공통 `BackgroundJobMessage`). Worker는 `jobId`로 `background_job`을 읽고 `reference_id`로 `analysis_report`를 조회해 나머지를 DB에서 얻는다. 발행은 at-least-once — Worker는 `jobId` 상태 CAS로 중복 수신을 멱등 처리하고, 리포트 상태 가드(AI-REPORT-004)는 순차 중복 완료를 막는 2차 방어다.
- 워커의 분석 입력 원천은 `analysis_report_source` 스냅샷이다 — `reference_id`로 얻은 리포트 id로 `seq_no` 순 조회하며, answer/question/member 원본 테이블은 조인하지 않는다.
```

`background_job_id` 1:1 유니크를 언급하는 표현과 "발행자가 역조회"라는 표현이 남아 있으면 모두 위 내용에 맞춰 정리한다.

- [ ] **Step 5: `AGENTS.md` 두 개를 대응 `CLAUDE.md`와 동기화**

`backgroundjob/AGENTS.md`와 `analysisreport/AGENTS.md`는 각각 같은 디렉터리 `CLAUDE.md`와 **3번 줄(도입 문장)만 다르고 나머지는 동일하다.** Step 3·4의 변경을 그대로 반영하되 3번 줄은 각 파일의 기존 문장을 유지한다.

- `CLAUDE.md` 3번 줄: `코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.`
- `AGENTS.md` 3번 줄: `이 컨텍스트를 수정할 때는 루트 `README.md`의 레이어 규칙을 따른다.`

```bash
diff src/main/java/site/one_question/api/backgroundjob/CLAUDE.md \
     src/main/java/site/one_question/api/backgroundjob/AGENTS.md
diff src/main/java/site/one_question/api/analysisreport/CLAUDE.md \
     src/main/java/site/one_question/api/analysisreport/AGENTS.md
```

기대: 두 diff 모두 3번 줄 한 곳만 차이. 다른 줄이 나오면 반영이 누락된 것이다.

- [ ] **Step 6: `BACKGROUND_JOB_ARCHITECTURE.md` 갱신**

`job_data`, `background_job_id`, "역조회" 언급이 10곳 있다. 모두 찾아 갱신한다.

```bash
grep -n "job_data\|background_job_id\|역조회" BACKGROUND_JOB_ARCHITECTURE.md
```

`README.md`는 갱신 대상이 아니다 — 패키지 트리(62-63번 줄)와 문서 포인터(150번 줄)에서만 두 컨텍스트를 언급하며, 컬럼명이나 FK 구조를 서술하지 않는다. Step 7의 grep으로 확인만 한다.

- [ ] **Step 7: 남은 참조가 없는지 확인**

```bash
grep -rn "job_data\|jobData\|background_job_id\|backgroundJobId\|findByBackgroundJob" \
  src/ erd.dbml README.md BACKGROUND_JOB_ARCHITECTURE.md
```

기대: 결과 없음.

- [ ] **Step 8: 최종 빌드**

```bash
./gradlew build
```

기대: PASS.

- [ ] **Step 9: 커밋**

```bash
git add erd.dbml BACKGROUND_JOB_ARCHITECTURE.md \
  src/main/java/site/one_question/api/backgroundjob/CLAUDE.md \
  src/main/java/site/one_question/api/backgroundjob/AGENTS.md \
  src/main/java/site/one_question/api/analysisreport/CLAUDE.md \
  src/main/java/site/one_question/api/analysisreport/AGENTS.md
git commit -m "docs(backgroundjob): reference_id 역전과 payload 역할 반영"
```

---

## Task 3 이후 수동 검증 (dev DB)

스펙의 "구현 중 검증할 것" 항목이다. `./gradlew` 테스트는 H2에서 돌기 때문에 Oracle 동작을 덮지 못한다.

Oracle dev DB에서 다음을 실행해 함수 기반 인덱스가 실제로 필요한지 확인한다.

```sql
-- 평범한 유니크 인덱스로 바꿔보고 NULL 2행을 시도한다
DROP INDEX uk_background_job_reference;
CREATE UNIQUE INDEX uk_background_job_reference_plain
    ON background_job (job_type, reference_id);

-- reference_id 가 NULL 인 같은 job_type 2건
INSERT INTO background_job (job_type, member_id, reference_id, payload, correlation_id,
    idempotency_key, request_hash, status, scheduled_at, retry_count)
VALUES ('ANALYSIS_REPORT', <기존 member id>, NULL, '{}', 'c1', 'k1',
    RPAD('a', 64, 'a'), 'PENDING', SYSTIMESTAMP, 0);
INSERT INTO background_job (job_type, member_id, reference_id, payload, correlation_id,
    idempotency_key, request_hash, status, scheduled_at, retry_count)
VALUES ('ANALYSIS_REPORT', <기존 member id>, NULL, '{}', 'c2', 'k2',
    RPAD('a', 64, 'a'), 'PENDING', SYSTIMESTAMP, 0);
```

- 두 번째 INSERT가 `ORA-00001`로 실패하면 → **CASE 트릭이 필요하다.** 평범한 인덱스를 DROP하고 마이그레이션의 함수 기반 인덱스를 복원한다.
- 두 번째 INSERT가 성공하면 → CASE 트릭이 불필요하다. 마이그레이션을 평범한 `CREATE UNIQUE INDEX uk_background_job_reference ON background_job (job_type, reference_id);`로 단순화하고, 관련 주석과 `backgroundjob/CLAUDE.md`의 설명도 함께 정리한다.

검증 후 테스트 데이터는 삭제한다.

---

## Self-Review

**1. 스펙 커버리지**

| 스펙 항목 | 태스크 |
|---|---|
| 결정 1 (`background_job_id` 삭제) | Task 3 Step 5, 12 |
| 결정 2 (`reference_id NUMBER` nullable, FK 없음) | Task 2 Step 3, 4 |
| 결정 3 (`job_type` 유지) | 변경 없음 — 어느 태스크도 건드리지 않음 |
| 결정 4 (`reference_type` 미추가 + 트리거 문서화) | Task 4 Step 3 |
| 결정 5 (`job_data`→`payload`, NOT NULL 유지) | Task 1 |
| 결정 6 (payload = `{}`) | Task 3 Step 9 |
| 결정 7 (1:1 함수 기반 유니크 인덱스) | Task 2 Step 3, 4 + 테스트 Step 1 |
| 결정 8 (마이그레이션 13번 직접 수정) | Task 1 Step 1, Task 2 Step 3, Task 3 Step 12 |
| 무변경: `request_hash` | 어느 태스크도 건드리지 않음 |
| 무변경: `member_id`, 멱등 유니크 키 | 어느 태스크도 건드리지 않음. 방향은 Task 4 Step 3에 기록 |
| 무변경: `BackgroundJobMessage` | 어느 태스크도 건드리지 않음 |
| DB 이식성 (PostgreSQL 단순화) | Task 2 Step 3 주석, Task 4 Step 1 note |
| `ddl-auto=validate` 의존 사실 | Task 2 Step 3 주석, Task 4 Step 3 |
| 데이터 흐름 (생성/발행/워커/소진) | Task 3 Step 9, 10 + Task 4 Step 4 |
| 테스트 (기존 5개 + 신규 2개) | Task 2 Step 1, Task 3 Step 1~3 |
| 문서 갱신 대상 6개 (`README.md`는 확인만 — 컬럼 구조 미서술) | Task 4 |
| 구현 중 Oracle 검증 | "Task 3 이후 수동 검증" 절 |

**스펙에서 의도적으로 제외:** `onPublishExhausted` 시그니처 변경 (위 "스펙에서 의도적으로 벗어난 결정" 참조).

**2. 플레이스홀더 스캔**

"TBD"/"TODO"/"적절히 처리"/"비슷하게" 없음. 모든 코드 스텝에 실제 코드 블록이 있고, 모든 실행 스텝에 명령어와 기대 결과가 있다. 수동 검증 SQL의 `<기존 member id>`는 실행 환경에서만 알 수 있는 값이므로 의도된 placeholder다.

**3. 타입 일관성**

- `BackgroundJob.create()`: Task 1에서 6개 파라미터(payload rename), Task 2에서 `referenceId`가 `member` 다음에 삽입되어 7개 — 두 태스크의 Interfaces 블록에 각각 명시했다.
- `BackgroundJob` 필드 순서 = `@AllArgsConstructor` 인자 순서. Task 2 Step 4에서 `referenceId`를 `member` 다음에 넣고 생성자 인자도 같은 위치에 넣으라고 명시했다.
- `AnalysisReport` 필드 9 → 8, `createPending` 인자 8개 — Task 3 Step 5에 개수를 적었다.
- `TestBackgroundJobUtils`: Task 1이 `createSave_With_Payload`를 만들고 Task 2가 `createSave_With_Reference`로 바꾸며 전자를 삭제한다. Task 2 Step 6에 삭제와 호출자 이관을 함께 적었다.
- `TestAnalysisReportUtils.createSave`: `(BackgroundJob, Member)` → `(Member)`. Task 3 Step 11.
- `AnalysisReportService`: `findByBackgroundJob`/`findByBackgroundJobId` → `findById(Long)`. Task 3 Step 7. `AnalysisReportJobPublisher`(Step 10)와 `AnalysisReportApplication`(Step 9)이 모두 `findById`를 쓴다 — 이름 일치.
- `BackgroundJobService.findById(Long): BackgroundJob` — Task 3 Step 8에서 정의, Step 10에서 사용.
