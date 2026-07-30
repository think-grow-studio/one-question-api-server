# 낙관적 락과 DDD — 동시 상태 전이를 도메인 모델을 지키면서 다루는 법

`BackgroundJob.enqueue()` 같은 상태 전이를 CAS로 처리해야 하는가를 논의하다가 정리한 문서.
결론부터: **낙관적 락(@Version)은 "JPA가 대신 써주는 CAS"이고, 동시성 제어를 영속성 계층이
흡수해주기 때문에 도메인 모델을 훼손하지 않는다. 이것이 JPA + DDD 궁합이 좋다고 말하는
핵심 이유 중 하나다.**

## 1. 문제: 엔티티 가드는 "동시" 중복을 못 막는다

이 프로젝트의 상태 전이는 엔티티 메서드가 가드한다:

```java
public void complete(String result, ...) {
    validatePending();          // PENDING이 아니면 도메인 예외 (AI-REPORT-004)
    this.result = result;
    ...
    this.status = AnalysisReportStatus.COMPLETED;
}
```

이 가드는 **메모리에 로드된 시점의 상태**를 검사한다. 그래서 방어 범위가 갈린다:

- **순차적 중복** — A가 커밋한 뒤 B가 조회: B는 COMPLETED를 읽고 가드에 걸림. ✅
- **동시 중복** — A, B가 둘 다 커밋 전에 조회: 둘 다 PENDING을 보고 가드를 통과,
  둘 다 UPDATE 성공, **마지막 커밋이 조용히 덮어쓴다.** ❌

전형적인 check-then-act 레이스다. SQS는 at-least-once 전달이라 같은 메시지를 두 워커가
동시에 처리하는 상황이 실제로 발생할 수 있고, 그 순간 이 구멍이 열린다.

## 2. 해법의 스펙트럼 — "CAS를 누가, 어느 층위에서 쓰느냐"

세 가지 선택지가 있고, 본질은 전부 "비교 후 갱신"이다. 차이는 작성 주체와 도메인 모델 보존 여부.

### ① 낙관적 락 `@Version` — JPA가 version으로 CAS

```java
@Version
private Long version;
```

JPA가 flush 시점에 자동으로 조건부 UPDATE를 만든다:

```sql
UPDATE analysis_report
SET status = 'COMPLETED', ..., version = 4   -- swap
WHERE id = ? AND version = 3                 -- compare (조회 시점에 읽어둔 값)
-- 영향 행 수 0 → 누군가 먼저 바꿈 → OptimisticLockException
```

- 엔티티 메서드, 가드, 도메인 예외가 **전부 그대로 유지**된다.
- 실패는 예외로 온다 → 호출자가 재시도/무시 정책을 정해야 한다.
- 충돌이 드문 경우의 정석.

### ② 비관적 락 `SELECT ... FOR UPDATE` — 락으로 경쟁 자체를 제거

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)   // 선언만으로 FOR UPDATE. JPQL 직접 작성 불필요
List<BackgroundJob> findAllByStatus(BackgroundJobStatus status, Pageable pageable);
```

- 락은 **조회 방식**의 문제다. 조회 후 변경은 여전히 순수 엔티티 메서드 → 도메인 모델 무손상.
- 여러 인스턴스가 같은 테이블을 폴링하는 DB 큐에서는 `SKIP LOCKED`(잠긴 행 건너뛰기)가
  업계 표준. 단, SKIP LOCKED는 JPA 표준이 아니라 Hibernate 힌트(lock timeout `-2`)나
  native query가 필요하다.

### ③ 조건부 벌크 UPDATE — 내가 status로 CAS (수동)

```java
@Modifying
@Query("UPDATE BackgroundJob j SET j.status = 'QUEUED' WHERE j.id = :id AND j.status = 'PUBLISHING' AND j.publishClaimId = :claimId")
int completePublish(
        @Param("id") Long id,
        @Param("claimId") String claimId
);   // 반환값 0이면 현재 발행 소유자가 아님
```

- 처리량이 아주 높거나 락 유지가 부담일 때. LLM 호출처럼 비싼 작업 전에
  "PROCESSING 선점"하는 패턴으로 실무에서 흔하다.
- 대가: 영속성 컨텍스트를 우회하고(flush/clear 주의), 엔티티 메서드의 가드·도메인 예외가
  무력화되며, 불변식이 두 곳(엔티티 + 쿼리 WHERE)에 존재하게 된다.
- 단, 이것도 DDD 포기가 아니다 — 불변식이 엔티티 메서드에서 **쿼리의 WHERE 절로 이사**한
  것뿐이고, 규칙은 여전히 도메인 레이어(리포지토리 + 도메인 서비스)에 산다.

## 3. 왜 낙관적 락이 DDD와 잘 맞는가

### 3-1. 도메인 모델이 동시성을 모른 채 순수하게 남는다

DDD의 목표는 비즈니스 규칙이 도메인 언어로, 도메인 레이어에 존재하는 것이다.
`report.complete(...)`는 "완료 처리에는 결과 4필드가 모두 필요하고, PENDING에서만 가능하다"는
규칙을 그대로 표현한다. `@Version`을 얹어도 이 코드는 **한 글자도 바뀌지 않는다.**
동시성이라는 기술 관심사를 영속성 계층(JPA)이 전부 흡수하기 때문이다.

반면 ③을 쓰면 규칙의 절반(상태 조건)이 JPQL 문자열로 이사하고, 엔티티 메서드는
호출되지 않는 장식이 되거나 삭제된다. 도메인 모델이 빈약해지는 방향이다.

### 3-2. 애그리거트 = 일관성 경계, version = 그 경계의 물리적 구현

DDD에서 애그리거트는 **트랜잭션 일관성의 경계**다("한 트랜잭션은 한 애그리거트만 수정").
`@Version`은 이 이론의 물리적 구현 장치다 — 같은 애그리거트 인스턴스를 두 트랜잭션이
겹쳐서 수정하면 경계 위반이고, version 충돌이 그것을 커밋 시점에 감지해준다.
즉 낙관적 락은 DDD에 "추가로 얹는 기술"이 아니라 **애그리거트 개념이 요구하는 보호를
그대로 제공하는 메커니즘**이다.

### 3-3. 실패가 도메인 의미로 번역 가능한 신호로 온다

`OptimisticLockException`은 "내가 조회한 이후 누군가 이 애그리거트를 바꿨다"는 정확한
사실을 알려준다. 호출자는 이것을 도메인 의미로 번역해 정책을 정한다:

- SQS 중복 메시지의 이중 완료 → "이미 처리됨"으로 간주하고 조용히 버림
- 사용자 동시 편집 → "다시 시도해주세요" 409 응답

수동 CAS(③)의 "영향 행 수 0"도 같은 정보지만, 낙관적 락은 이것을 모든 필드 변경에 대해
일관된 방식으로, 자동으로 제공한다.

## 4. 트레이드오프와 함정

- **충돌이 잦은 핫 로우에는 부적합.** 실패 = 트랜잭션 전체 재시도라서, 상시 경쟁하는
  카운터류에는 조건부 UPDATE나 락이 낫다. 낙관락은 이름대로 "충돌이 드물다"에 베팅한다.
- **벌크 UPDATE는 version 검사를 우회한다.** 낙관락 걸린 엔티티를 `@Modifying` JPQL로
  고치는 코드가 섞이면 보호에 구멍이 뚫린다.
- **예외 처리 정책 없이 넣으면 의미불명 500만 늘어난다.** version 컬럼 + 예외를 도메인
  의미로 번역하는 처리 + 그 경로를 검증하는 테스트가 한 세트다.
- **경쟁 주체가 없는데 미리 넣지 말 것.** 실행할 방법이 없는 방어 코드는 테스트 불가능한
  죽은 코드다. 트리거는 "같은 행을 만지는 두 번째 쓰기 주체의 등장"이다.

## 5. 스택별 현실 — ORM 성숙도가 선택지를 결정한다

| 스택 | 낙관적 락 지원 | 실무 관용구 |
|---|---|---|
| Spring + JPA/Hibernate | `@Version` (JPA 표준) | ① 낙관락 |
| FastAPI + SQLAlchemy | `version_id_col` | ① 낙관락 |
| NestJS + Prisma | 내장 없음 | ③ `updateMany({ where: { id, version } })` 수동 CAS |
| Go (Gin 등) | GORM 플러그인 정도 | ③ raw SQL 조건부 UPDATE 또는 ② FOR UPDATE |
| Django | 내장 없음 | ③ `filter(...).update(...)` 또는 ② `select_for_update(skip_locked=True)` |

요약: **성숙한 ORM 스택은 ①, 경량 ORM/쿼리빌더 스택은 ③, DB 큐 폴링은 스택 무관 ②.**
②의 SKIP LOCKED는 ORM 기능이 아니라 SQL 영역(Oracle/Postgres/MySQL 8+)이라 어디서든 쓸 수 있다.

## 6. 이 프로젝트의 적용 판단

- **SQS Publisher: ③ status + claim CAS를 사용한다 (2026-07 확정).** ShedLock은 공통
  스케줄 실행을 1차 단일화하고, 행 단위 CAS는 락 만료·수동 실행·향후 다른 진입점을
  2차 방어한다. `PENDING → PUBLISHING` 선점 후 외부 호출은 트랜잭션 밖에서 수행하며,
  `publish_claim_until`이 지난 작업은 현재 claim ID까지 일치할 때만 복구한다.
- **AI 워커(Python, 별도 런타임) 도입 시: ③ 조건부 UPDATE로 확정 (2026-07 논의).**
  - 워커는 `analysis_report_source`(분석 재료)를 읽어야 하므로 DB 결합이 어차피 발생한다.
    메시지에 본문을 실으면 SQS 256KB 상한 초과 위험(답변 15개 × 5000자 한글 ≈ 225KB) —
    소스 스냅샷 테이블이 사실상 Claim Check 저장소 역할이다.
  - 워커는 JPA 밖(Python)이라 `@Version`의 핵심 이점(도메인 메서드 무손상)이 성립하지
    않는다. 언어 불문 동작하는 status 기반 CAS가 맞다:
    - LLM 호출 전 선점: `UPDATE background_job SET status='PROCESSING' WHERE id=? AND status='QUEUED'`
    - 완료 기록: `UPDATE analysis_report SET status='COMPLETED', ... WHERE id=? AND status='PENDING'`
    - rowcount 0 = 중복 메시지로 간주하고 버린다.
  - "shared database 안티패턴"은 서로 다른 팀의 독립 서비스가 DB로 통합할 때 얘기다.
    한 프로덕트의 두 런타임이 DB를 공유하는 것은 일반적이며(Celery-Django 관계와 동일),
    대신 규율이 필요하다: 쓰기 계약을 위 2~3문장으로 고정, 읽기 범위 명시, 워커 전용
    DB 계정(최소 grant), 스키마 변경 시 워커 동시 고려.
- 어드민에 "작업 재발행" 같은 기능이 생겨 스케줄러와 같은 행을 만지게 되는 경우는
  Java 세계 안의 경쟁이므로 그때는 `@Version`이 다시 1순위 후보다.
