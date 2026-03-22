# ORA-12860 데드락 장애 분석 보고서

> **발생일시**: 2026-03-22
> **영향 범위**: 회원 탈퇴 API (`DELETE /api/v1/auth/me`)
> **에러 코드**: `ORA-12860: deadlock detected while waiting for a sibling row lock`
> **해결 방법**: Oracle ADB 접속 서비스를 `_high` → `_tp`로 변경

---

## 1. 문제 현상

```
Caused by: ORA-12860: deadlock detected while waiting for a sibling row lock
  → DELETE FROM daily_question WHERE member_id = ?
```

회원 탈퇴 시 `daily_question` 테이블 삭제 쿼리에서 데드락이 발생하며 500 에러 반환.
재시도해도 **동일하게 실패**하는 재현성 있는 장애였음.

---

## 2. 배경 지식

### 2-1. Oracle 락(Lock) 종류

Oracle은 동시성 제어를 위해 여러 수준의 락을 사용한다.

```
락 종류
├── Row-level Lock (TX Lock)
│   ├── X (Exclusive)  : INSERT / UPDATE / DELETE 시 획득
│   └── S (Share)      : 일부 FK 체크 시 사용
│
└── Table-level Lock (TM Lock)
    ├── SS (Sub-Share)       : SELECT FOR UPDATE
    ├── SX (Sub-Exclusive)   : DML (일반적)
    ├── S  (Share)           : FK 인덱스 없을 때 FK 체크
    └── X  (Exclusive)       : DDL
```

**핵심**: DML(INSERT/UPDATE/DELETE)은 대상 행에 **X 락**을 획득한다.
같은 트랜잭션 내에서는 자기 자신의 락에 의해 블록되지 않는다.

### 2-2. ORA-00060 vs ORA-12860

| 구분 | ORA-00060 | ORA-12860 |
|------|-----------|-----------|
| 이름 | deadlock detected | deadlock detected while waiting for a **sibling** row lock |
| 주체 | **서로 다른 트랜잭션** 간 교착 | **동일 문장 내 병렬 slave** 간 교착 |
| Oracle 처리 | victim 1개 롤백, 나머지 성공 | 해당 문장 전체 실패 |
| 발생 조건 | 잠금 순서 교차 | 병렬 DML slave 간 잠금 경합 |

> **"sibling"** = 같은 부모(coordinator)를 두고 동시에 실행되는 형제 slave 프로세스

---

## 3. Oracle 병렬 처리 구조

Oracle의 병렬 실행은 **Coordinator + Slave** 구조로 동작한다.

```
┌─────────────────────────────────────────────────────┐
│                   Query Coordinator                  │
│         (DELETE FROM daily_question WHERE ...)       │
└──────────┬──────────┬──────────┬──────────┬─────────┘
           │          │          │          │
    ┌──────▼──┐ ┌─────▼───┐ ┌───▼─────┐ ┌─▼───────┐
    │ Slave 1 │ │ Slave 2 │ │ Slave 3 │ │ Slave 4 │
    │rows 1~25│ │rows26~50│ │rows51~75│ │rows76~  │
    └─────────┘ └─────────┘ └─────────┘ └─────────┘
```

각 Slave는 **담당 행 범위를 독립적으로** 처리한다.
이때 각 Slave는:
1. 테이블 데이터 블록에 X 락
2. 인덱스 블록 수정 (UK 인덱스: `member_id, question_date`)

### 문제의 핵심: 병렬 DML에서의 인덱스 잠금 경합

```
테이블 행과 인덱스 블록은 별개의 물리적 공간에 있다.

[테이블 데이터 블록]          [인덱스 블록]
┌──────────────────┐          ┌──────────────────┐
│ Row A (member=1) │          │ Index entry A    │
│ Row B (member=1) │          │ Index entry B    │
│ Row C (member=1) │          │ Index entry C    │
└──────────────────┘          └──────────────────┘
```

**데드락 발생 시나리오:**

```
시각   Slave 1                          Slave 2
─────  ─────────────────────────────   ─────────────────────────────
T1     Row A 데이터 블록 X락 획득      Row B 데이터 블록 X락 획득
T2     인덱스 블록 X 수정 시도         인덱스 블록 X 수정 시도
       └→ Slave 2가 잠고 있음, 대기    └→ Slave 1이 잡고 있음, 대기
T3     ↑ 서로가 서로를 기다리는 상태 ↑
       ════════ ORA-12860 발생 ═════════
```

> Slave들은 **인덱스를 수정하는 순서가 정해져 있지 않다**.
> 테이블 행은 분배됐지만, `(member_id, question_date)` 유니크 인덱스는
> 모든 Slave가 **공유하며 수정**해야 하기 때문에 경합이 발생한다.

---

## 4. 이 장애의 정확한 원인 분석

### 4-1. 환경 파악

```sql
-- 확인된 Oracle ADB 병렬 설정
parallel_degree_policy  = AUTO    ← 핵심 원인
parallel_min_degree     = CPU     ← 최소 DOP = CPU 개수
parallel_max_servers    = 12
parallel_force_local    = FALSE
```

- `parallel_degree_policy=AUTO`: Oracle이 SQL 문장마다 **자동으로 DOP를 결정**
- `parallel_min_degree=CPU`: DOP 최솟값이 CPU 개수 → **무조건 병렬**
- Oracle ADB의 `_high` 서비스: **HIGH consumer group = 최대 병렬**

### 4-2. 접속 서비스와 consumer group 관계

Oracle ADB는 접속 서비스 이름으로 병렬 수준을 제어한다.

```
접속 서비스      Consumer Group    병렬 수준
─────────────   ───────────────   ──────────────────────────
_high           HIGH              최대 병렬 (DOP = CPU 수)
_medium         MEDIUM            중간 병렬 (DOP 제한)
_low            LOW               최소 병렬 (직렬에 가까움)
_tp             TP                OLTP 최적화 (직렬/낮은 DOP)
_tpurgent       HIGH              TP 중 긴급 쿼리
```

### 4-3. 장애 발생 흐름 전체

```
[클라이언트] DELETE /api/v1/auth/me
      │
      ▼
[AuthApplication.withdraw()]  ← @Transactional (단일 트랜잭션)
      │
      ├─① refreshToken 삭제    ✅
      ├─② dailyQuestionAnswer 삭제  ✅  (X락 보유 중)
      ├─③ dailyQuestion 삭제   ❌ ORA-12860
      │
      │  Oracle ADB (_high + AUTO DOP)
      │  ┌─────────────────────────────────────────────┐
      │  │  DELETE FROM daily_question WHERE member_id=?│
      │  │                                             │
      │  │  Coordinator가 DOP=4로 병렬 실행 결정       │
      │  │                                             │
      │  │  Slave1: 행1~3 삭제 → UK인덱스 수정 시도   │
      │  │  Slave2: 행4~6 삭제 → UK인덱스 수정 시도   │
      │  │  Slave3: 행7~9 삭제 → UK인덱스 수정 시도   │
      │  │  Slave4: 행10~  삭제 → UK인덱스 수정 시도  │
      │  │                                             │
      │  │  → 인덱스 블록 경합 → ORA-12860            │
      │  └─────────────────────────────────────────────┘
      │
      ▼
[트랜잭션 전체 롤백]
```

### 4-4. 왜 재시도해도 계속 실패했는가?

일반적인 ORA-00060(교차 트랜잭션 데드락)은 Oracle이 victim을 골라 한 쪽만 실패시키므로, 재시도하면 성공 가능성이 있다.

그러나 ORA-12860은 **단일 문장 내부에서 발생**하므로:
- 재시도해도 동일한 병렬 구조로 실행
- 동일한 인덱스 경합 패턴 발생
- **매번 실패** → 재현성 있는 장애

---

## 5. 해결 시도 과정

### ❌ 시도 1: HikariCP connectionInitSql

```yaml
spring.datasource.hikari.connection-init-sql: ALTER SESSION SET parallel_degree_policy=MANUAL
```

**결과**: `ORA-01031: insufficient privileges`
→ Oracle ADB는 일반 유저의 `ALTER SESSION SET parallel_degree_policy` 차단

### ❌ 시도 2: NO_PARALLEL 쿼리 힌트

```java
@Query(value = "DELETE /*+ NO_PARALLEL */ FROM daily_question WHERE member_id = :memberId",
       nativeQuery = true)
```

**결과**: 여전히 ORA-12860
→ `NO_PARALLEL`은 SELECT 병렬화 제어용. DML 병렬화에는 효과 없음

### ❌ 시도 3: DBMS_CLOUD_ADMIN

```sql
BEGIN
  DBMS_CLOUD_ADMIN.SET_DATABASE_PARAMETER(
    parameter => 'parallel_degree_policy', value => 'MANUAL'
  );
END;
```

**결과**: `PLS-00302: 'SET_DATABASE_PARAMETER' 구성요소가 정의되어야 합니다`
→ 해당 Oracle ADB 버전에서 프로시저 미지원

### ❌ 시도 4: ALTER SYSTEM (ADMIN 계정)

```sql
ALTER SYSTEM SET parallel_degree_policy=MANUAL SCOPE=BOTH;
```

**결과**: `ORA-01031: insufficient privileges`
→ Oracle ADB는 ADMIN 계정도 `ALTER SYSTEM` 차단 (완전 관리형 서비스)

### ✅ 시도 5: 접속 서비스 변경 `_high` → `_tp`

```
# 변경 전
ORACLE_DB_URL=jdbc:oracle:thin:@...(service_name=dbname_high)...

# 변경 후
ORACLE_DB_URL=jdbc:oracle:thin:@...(service_name=dbname_tp)...
```

**결과**: 정상 동작 ✅

---

## 6. 해결 원리

`_tp` 서비스는 Oracle ADB에서 **OLTP 워크로드 전용**으로 설계된 consumer group이다.

```
_high 서비스 (변경 전)
┌────────────────────────────────────────┐
│ Consumer Group: HIGH                   │
│ DOP: AUTO (CPU 수만큼 자동 결정)       │
│ DELETE → 4개 Slave 병렬 실행           │
│ → 인덱스 블록 경합 → ORA-12860        │
└────────────────────────────────────────┘

_tp 서비스 (변경 후)
┌────────────────────────────────────────┐
│ Consumer Group: TP                     │
│ DOP: 직렬 또는 매우 낮은 DOP          │
│ DELETE → 단일 프로세스 순차 실행       │
│ → 인덱스 블록 경합 없음 → 성공        │
└────────────────────────────────────────┘
```

병렬 Slave가 없으니 "sibling" 자체가 존재하지 않아 ORA-12860이 원천적으로 발생 불가.

---

## 7. 재발 방지 및 추가 권장사항

### 7-1. 서비스 선택 가이드

| 용도 | 권장 서비스 |
|------|-------------|
| API 서버, 앱 서버 (OLTP) | `_tp` |
| 배치, ETL, 대량 분석 | `_medium` or `_low` |
| 긴급 쿼리, 관리 작업 | `_tpurgent` |
| DW 분석, 보고서 | `_high` |

> **`_high`는 API 서버에서 사용하면 안 된다.**
> OLTP 특성상 짧은 트랜잭션이 많은데 과도한 병렬화가 오히려 성능과 안정성을 해친다.

### 7-2. 현재 쿼리 힌트 정리

해결 과정에서 아래 힌트가 추가됐으나, `_tp` 서비스로 해결됐으므로 **원복해도 무방**하다.

```java
// 현재 (힌트 추가됨)
@Query(value = "DELETE /*+ DISABLE_PARALLEL_DML NO_PARALLEL(dq) */ FROM daily_question dq WHERE dq.member_id = :memberId", nativeQuery = true)

// 원복 가능 (JPQL)
@Query("DELETE FROM DailyQuestion dq WHERE dq.member.id = :memberId")
```

다만 힌트를 유지해도 동작에 문제 없으며, 방어적 관점에서 유지해도 된다.

---

## 8. 요약

```
근본 원인:
  Oracle ADB를 _high 서비스로 접속
  → HIGH consumer group → parallel_degree_policy=AUTO + parallel_min_degree=CPU
  → DELETE 문장을 자동으로 다중 Slave 병렬 실행
  → 형제 Slave들이 UK 인덱스 블록을 엇갈린 순서로 잠그며 ORA-12860 데드락

해결:
  접속 서비스를 _high → _tp 변경
  → TP consumer group = OLTP 모드 = 직렬 실행
  → Slave 없음 = sibling 없음 = ORA-12860 불가
```
