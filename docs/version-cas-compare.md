# 낙관적 락(version) vs 상태 CAS

동시성 제어에서 자주 헷갈리는 두 기법을 구체 사례로 정리한다.

## TL;DR

- **둘 다 본질은 CAS**(Compare-And-Swap)다. "내가 기대한 값일 때만 바꾼다."
- **version(낙관적 락)** = `version` 컬럼 하나로 **row 전체의 변경**을 감지. 균일·자동·ABA 안전.
- **status CAS / 값 기반 CAS** = 특정 컬럼(들)의 값만 가드. 더 세밀하지만, 까먹을 위험 + ABA 취약.
- **상위호환 관계가 아니라 트레이드오프**다: `세밀함 ↔ 안전·단순`.
- 결론(우리 프로젝트): job 클레임은 **status CAS**, 복잡한 read-modify-write가 생기면 **version**.

---

## 1. 두 기법의 실제 모습

### version (낙관적 락)

읽을 때 `version`을 같이 읽고, 쓸 때 그 값이 그대로인지 확인하며 +1 한다.

```sql
-- 읽기: id=1, version=5, status='PENDING' 을 읽었다고 하자
UPDATE background_job
   SET status = 'PROCESSING', version = version + 1
 WHERE id = 1 AND version = 5;
-- affected rows = 1 → 성공 (내가 읽은 뒤로 아무도 안 건드림)
-- affected rows = 0 → 실패 (그 사이 누군가 이 row를 바꿈) → 재시도
```

가드 = **"내가 읽은 뒤로 이 row가 하나도 안 바뀌었나"**. 어떤 필드가 바뀌든 version이 올라가므로 전부 감지한다.

### status CAS (값 기반 CAS)

비교할 값을 직접 WHERE에 박는다. 보통 읽지 않고 바로 단언한다.

```sql
UPDATE background_job
   SET status = 'PROCESSING', started_at = now()
 WHERE id = 1 AND status = 'PENDING';
-- affected rows = 1 → 내가 점유 성공
-- affected rows = 0 → 누가 이미 가져감
```

가드 = **"status가 아직 PENDING인가"** 딱 하나. 다른 필드 변경은 신경 쓰지 않는다.

> 낙관적 락은 결국 "version 컬럼에 대한 CAS"다. 그래서 둘은 같은 가족이고, 차이는 **무엇을 비교하느냐**다.

---

## 2. 핵심 차이

| 항목 | version | 값 기반 CAS |
|---|---|---|
| 가드 대상 | row 전체(어떤 변경이든) | 지정한 컬럼 값만 |
| 먼저 읽어야? | 그렇다(read-modify-write) | 아니다(precondition 단언) |
| 세밀함 | 거침(false conflict 가능) | 세밀(서로 다른 필드면 충돌 안 남) |
| ABA 안전성 | 안전(단조 증가) | 취약(A→B→A를 못 봄) |
| 까먹을 위험 | 낮음(ORM 자동 가능) | 높음(모든 경로에 조건 필요) |
| 까다로운 타입 | 무관(정수 1개) | NULL/float/BLOB 비교 함정 |

---

## 3. 구체 사례

### 사례 A — job 클레임: status CAS가 적합

요구사항: "PENDING인 job을 워커 **딱 하나만** 잡아서 PROCESSING으로."

이건 명확한 단일 전이다. status CAS 한 문장이면 끝나고, 먼저 읽을 필요도 없다.

```sql
UPDATE background_job SET status='PROCESSING'
 WHERE id=? AND status='PENDING';   -- affected==1 이면 점유 성공
```

version으로도 되지만 (1) 먼저 읽어야 하고 (2) 상관없는 필드 변경에도 실패하는 불필요한 충돌이 생긴다. → **이 경우엔 CAS가 더 단순하고 정확.**

### 사례 B — ABA 문제: version이 막아준다

status가 순환할 수 있을 때(`PENDING → PROCESSING → PENDING`) 값 CAS가 당한다.

```
t0  워커 A: status = PENDING 으로 읽음
t1  워커 B: PENDING → PROCESSING  (B가 먼저 잡음)
t2  B 실패 → 복구 로직이 PROCESSING → PENDING 으로 리셋
t3  워커 A: CAS  WHERE status='PENDING'  → 지금도 PENDING이라 ✅ 성공!
```

A는 "깨끗한 job을 방금 잡았다"고 믿지만, 사실 그 사이 B가 처리하다 실패했다.
B가 남긴 부작용(retry_count 증가, 외부 API 호출, 반쯤 쓴 결과)을 모른 채 stale하게 진행한다.

version이면:

```
t0  A가 version=5 읽음
t1  B 점유 → version=6
t2  리셋   → version=7
t3  A의 CAS  WHERE version=5  → ❌ 실패 (지금 7)
    → A는 "내가 본 뒤로 바뀌었다"를 정확히 감지하고 다시 읽음
```

값이 우연히 PENDING으로 돌아와도 version은 절대 옛 값으로 안 돌아오므로 ABA가 막힌다.

> ABA의 원형은 lock-free 스택이다. 노드를 pop → 다른 노드 push → 원래 노드 다시 push 하면 head 포인터가 같은 주소(A)로 돌아오고, CAS는 성공하지만 구조는 망가진다. DB 버전이 위 타임라인이다.

### 사례 C — 값 CAS의 세밀함(장점)

한 row를 두 주체가 **서로 다른 필드**로 동시 수정.

```
워커: PENDING → PROCESSING   (status만)
어드민: priority 1 → 2        (priority만)
```

- 값 CAS: 워커는 `WHERE status='PENDING'`만 보므로 priority 변경과 **충돌 없이 둘 다 성공**.
- version: priority 변경으로 version이 올라가 워커 업데이트가 **불필요하게 실패**(false conflict).

→ 충돌이 잦은 고동시성 환경에서 값 CAS가 처리량을 높일 수 있다.

### 사례 D — 값 CAS의 함정(단점)

1) **까먹기**: 어떤 코드가 가드 없이 `UPDATE ... WHERE id=?`를 날리면 그 순간 lost update. 모든 write 경로가 빠짐없이 조건을 넣어야 한다. version(특히 `@Version`)은 모든 update에 자동으로 붙어 까먹을 수 없다.

2) **NULL 비교**: SQL에서 `col = NULL` 은 항상 false다. nullable 필드를 값 CAS로 비교하려면 null-safe 비교가 필요하다.

```sql
-- 틀림: memo가 NULL이면 영원히 매칭 안 됨
WHERE memo = NULL
-- 맞음(Postgres)
WHERE memo IS NOT DISTINCT FROM ?
```

3) **타입 비용**: float 정밀도, 큰 text/BLOB 비교 비용 등. version은 정수 1개라 무관.

---

## 4. 언제 무엇을 쓰나

- **단일 상태 전이(클레임/예약/토글)** → **status CAS**. 단순·정확.
- **임의 필드의 read-modify-write, 전체 불변식 보호** → **version**. 안전한 기본값.
- **고동시성 + 필드별 충돌 분리가 진짜 필요** → 값 기반 CAS(예: Hibernate `OptimisticLockType.ALL/DIRTY`). 복잡성 감수하고 꺼내 쓰는 도구.
- **status가 순환(A→B→A)** → version이나 `lease_token`을 곁들여 ABA 차단.

---

## 5. 우리 프로젝트 결론

- 다투는 작업이 "job 클레임"이라는 단방향 전이라 **status CAS로 충분**하다.
- FastAPI(생 SQL/SQLAlchemy)에서 `affected rows` 확인으로 명시적 구현이 깔끔하다.
- 안전망: **단방향 상태머신 + SQS visibility timeout + 결과 INSERT 멱등키.** 이 조합이면 MVP에선 version 없이도 이중 처리/ABA가 충분히 걸러진다.
- 나중에 job row를 여러 주체가 복잡하게 동시 수정하는 상황이 생기면 그때 `version`을 도입한다.