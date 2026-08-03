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

**실제 로그:**
```
2026-03-22 13:22:53 [member:106] ORA-12860 → 실패
2026-03-22 13:33:18 [member:105] ORA-12860 → 실패
2026-03-22 13:33:20 [member:105] ORA-12860 → 재시도, 또 실패
```

핵심 단서: **재시도해도 100% 실패** → 동시 요청 간 충돌이 아니라 **단일 문장 내부의 구조적 문제.**

---

## 2. 배경 지식

### 2-1. Oracle 락(Lock) 종류

Oracle은 동시성 제어를 위해 여러 수준의 락을 사용한다.

```
락 종류
├── Row-level Lock (TX Lock)
│   ├── X (Exclusive)  : INSERT / UPDATE / DELETE 시 획득. 다른 TX의 X락과 충돌
│   └── S (Share)      : FK 무결성 체크 시 사용. 다른 TX의 X락과 충돌
│
└── Table-level Lock (TM Lock)
    ├── SS (Sub-Share)       : SELECT FOR UPDATE
    ├── SX (Sub-Exclusive)   : DML 시 자동 획득 (행 단위가 아닌 테이블 의도 잠금)
    ├── S  (Share)           : FK 인덱스 없을 때 전체 테이블 FK 체크
    └── X  (Exclusive)       : DDL (ALTER TABLE 등)
```

**핵심**: DML(INSERT/UPDATE/DELETE)은 대상 행에 **X 락**을 획득한다.
같은 트랜잭션 내에서는 자기 자신의 락에 의해 블록되지 않는다.
**하지만 같은 트랜잭션의 병렬 slave끼리는 서로의 락에 블록될 수 있다.**

### 2-2. ORA-00060 vs ORA-12860 — 일반 데드락과 형제 데드락

```
ORA-00060: 일반 데드락                     ORA-12860: 형제 데드락
─────────────────────────                  ─────────────────────────

  TX-A        TX-B                          DELETE 문장 1개
  ┌──┐        ┌──┐                         ┌──────────────┐
  │  │──X락──→│  │                         │ Coordinator  │
  │  │←─X락──│  │                         └──┬───────┬───┘
  └──┘        └──┘                            │       │
                                          ┌───▼──┐ ┌──▼───┐
  서로 다른 트랜잭션이                    │Slave1│ │Slave2│
  서로의 행을 기다림                      └───┬──┘ └──┬───┘
                                              │       │
  → victim 1개 죽이면                         └─X락교차┘
    나머지 성공
                                          같은 문장의 형제 프로세스가
                                          서로의 인덱스 블록을 기다림

                                          → 문장 자체가 구조적으로 실패
                                            재시도해도 동일 패턴 반복
```

| 구분 | ORA-00060 | ORA-12860 |
|------|-----------|-----------|
| 주체 | 서로 **다른 트랜잭션** | **같은 문장**의 병렬 slave들 |
| Oracle 처리 | victim 1개 롤백, 나머지 성공 | 해당 문장 **전체 실패** |
| 재시도 시 | 성공 가능 (상대가 이미 끝남) | **동일하게 실패** (구조적 문제) |

> **"sibling"** = 같은 부모(coordinator)를 두고 동시에 실행되는 형제 slave 프로세스

---

## 3. Oracle 병렬 DML 구조와 데드락 발생 원리

### 3-1. Coordinator + Slave 구조

Oracle이 DML을 병렬로 실행할 때, **Coordinator**가 작업을 **Slave**들에게 분배한다.

```
┌──────────────────────────────────────────────────────┐
│                   Query Coordinator                   │
│          DELETE FROM daily_question WHERE ...         │
│                                                      │
│          "이 행들을 나눠서 처리해라"                  │
└──────────┬──────────┬──────────┬──────────┬──────────┘
           │          │          │          │
    ┌──────▼──┐ ┌─────▼───┐ ┌───▼─────┐ ┌─▼───────┐
    │ Slave 1 │ │ Slave 2 │ │ Slave 3 │ │ Slave 4 │
    │Block100 │ │Block200 │ │Block300 │ │Block400 │
    │ ~199    │ │ ~299    │ │ ~399    │ │ ~       │
    └─────────┘ └─────────┘ └─────────┘ └─────────┘

    각 Slave는 데이터 블록 범위별로 행을 분배받아 독립 실행
```

### 3-2. 테이블과 인덱스의 물리적 배치 차이 (핵심)

**이것이 데드락의 근본 원인이다.**

```
                ┌─ 테이블: 행이 INSERT 순서대로 흩어져 저장됨
                │
                │  Data Block 100        Data Block 200        Data Block 300
                │  ┌───────────────┐     ┌───────────────┐     ┌───────────────┐
                │  │ Row A (03-01) │     │ Row B (03-02) │     │ Row C (03-03) │
                │  │ Row F (03-06) │     │ Row E (03-05) │     │ Row D (03-04) │
                │  └───────────────┘     └───────────────┘     └───────────────┘
                │
                │  → Slave들에게 블록 단위로 분배됨
                │    Slave 1 → Block 100 (Row A, F)
                │    Slave 2 → Block 200 (Row B, E)
                │
                │
                └─ UK 인덱스: 키 값 (member_id, question_date) 순서로 정렬 저장됨

                   Index Leaf Block ①              Index Leaf Block ②
                   ┌────────────────────┐          ┌────────────────────┐
                   │ (106, 03-01) → A   │          │ (106, 03-04) → D   │
                   │ (106, 03-02) → B   │          │ (106, 03-05) → E   │
                   │ (106, 03-03) → C   │          │ (106, 03-06) → F   │
                   └────────────────────┘          └────────────────────┘

                   → Slave 분배와 무관하게 키 값 순서로 배치됨
                     어떤 Slave든 같은 인덱스 블록을 수정해야 할 수 있음
```

**문제:** Slave 1은 Block 100의 행(A, F)을 처리하지만,
인덱스에서는 Leaf ①(03-01)과 Leaf ②(03-06)를 **모두** 수정해야 함.
Slave 2도 Block 200의 행(B, E)을 처리하면서
Leaf ①(03-02)과 Leaf ②(03-05)를 **모두** 수정해야 함.

→ **두 Slave가 같은 인덱스 블록을 교차 접근**하게 됨.

### 3-3. 데드락 발생 시나리오 (단계별)

```
회원 106의 daily_question 데이터:

  Row A: id=10, date='03-01'  (Data Block 100)
  Row B: id=25, date='03-02'  (Data Block 200)
  Row E: id=40, date='03-05'  (Data Block 200)
  Row F: id=55, date='03-06'  (Data Block 100)

Coordinator 분배:
  Slave 1 → Block 100 → Row A(03-01), Row F(03-06)
  Slave 2 → Block 200 → Row B(03-02), Row E(03-05)
```

```
시각   Slave 1                              Slave 2
─────  ────────────────────────────────     ────────────────────────────────

T1     ▶ Row A 삭제 시작                    ▶ Row B 삭제 시작
       Block 100에서 Row A에 X락 획득 ✅    Block 200에서 Row B에 X락 획득 ✅


T2     Index Leaf ①에서                     Index Leaf ①에서
       (106,03-01) 엔트리 X락 & 삭제 ✅     (106,03-02) 엔트리 X락 & 삭제 ✅

       ※ 둘 다 Leaf ①을 점유 중이지만
         서로 다른 엔트리이므로 아직 충돌 없음


T3     ▶ Row F 삭제 시작                    ▶ Row E 삭제 시작
       Block 100에서 Row F에 X락 획득 ✅    Block 200에서 Row E에 X락 획득 ✅


T4     Index Leaf ②에서                     Index Leaf ②에서
       (106,03-06) 엔트리 수정 시도         (106,03-05) 엔트리 수정 시도

       ┃ Slave 2가 Leaf ② 구조를            ┃ Slave 1이 Leaf ② 구조를
       ┃ 수정 중 → 대기                     ┃ 수정 중 → 대기
       ┃                                    ┃
       ▼                                    ▼

       ╔════════════════════════════════════════════════════════════╗
       ║                                                            ║
       ║   Slave 1: Leaf ② 접근 대기 중 (Slave 2가 점유)          ║
       ║   Slave 2: Leaf ② 접근 대기 중 (Slave 1이 점유)          ║
       ║                                                            ║
       ║   또는 Leaf ①과 ②를 교차 점유하며 교착                   ║
       ║                                                            ║
       ║              ⚡ ORA-12860 발생 ⚡                          ║
       ║                                                            ║
       ╚════════════════════════════════════════════════════════════╝
```

### 3-4. 왜 직렬 실행에서는 문제가 없는가?

```
_tp 서비스 (직렬): 프로세스 1개가 순서대로 처리

  프로세스 → Row A 삭제 → Leaf ① 수정
           → Row B 삭제 → Leaf ① 수정
           → Row C 삭제 → Leaf ① 수정
           → Row D 삭제 → Leaf ② 수정
           → Row E 삭제 → Leaf ② 수정
           → Row F 삭제 → Leaf ② 수정
                    ↑
         혼자서 순차 처리하므로 교차 접근 불가능 → 데드락 없음


_high 서비스 (병렬): Slave 여러 개가 동시 처리

  Slave 1 → Row A(Leaf ①) → Row F(Leaf ②) ─┐
                                               ├→ Leaf ①②를 교차 접근 → 💥
  Slave 2 → Row B(Leaf ①) → Row E(Leaf ②) ─┘
```

**핵심 한 줄:**
> 테이블 행은 Slave별로 나뉘지만, 인덱스 블록은 나뉘지 않는다.
> 여러 Slave가 같은 인덱스 블록을 동시에 수정하면 교착이 발생한다.

---

## 4. 이 장애의 정확한 원인 분석

### 4-1. 환경 파악

```sql
-- 확인된 Oracle ADB 병렬 설정 (변경 전 / 변경 후 동일)
parallel_degree_policy  = AUTO    ← 핵심: Oracle이 DOP를 자동 결정
parallel_min_degree     = CPU     ← 최소 DOP = CPU 개수 → 사실상 무조건 병렬
parallel_max_servers    = 12
parallel_force_local    = FALSE
```

> 이 파라미터들은 DB 레벨 설정으로, `_tp`로 변경 후에도 값 자체는 동일하다.
> 차이는 **접속 서비스(consumer group)가 이 설정을 어디까지 허용하느냐**에 있다.

### 4-2. 접속 서비스와 consumer group 관계

Oracle ADB는 접속 서비스 이름으로 **세션에 적용되는 병렬 수준**을 제어한다.

```
접속 서비스      Consumer Group    병렬 수준              용도
─────────────   ───────────────   ─────────────────────  ──────────────────
_high           HIGH              최대 (DOP = CPU 수)    DW, 대량 분석
_medium         MEDIUM            중간 (DOP 제한)        보고서, 배치
_low            LOW               최소 (직렬에 가까움)   저우선 배치
_tp             TP                직렬/최저 DOP          OLTP, API 서버 ✅
_tpurgent       TPURGENT          TP 중 우선순위 높음    긴급 OLTP 쿼리
```

### 4-3. 장애 발생 흐름

```
[클라이언트] DELETE /api/v1/auth/me (회원탈퇴)
      │
      ▼
[AuthApplication.withdraw()]  ← @Transactional (단일 트랜잭션)
      │
      ├─① refreshTokenService.deleteByMemberId       ✅
      ├─② dailyQuestionAnswerService.deleteByMemberId ✅ (X락 보유 중)
      ├─③ dailyQuestionService.deleteByMemberId       ❌ ORA-12860 !!!
      │      │
      │      │  Oracle 내부 (_high 서비스 + AUTO DOP)
      │      │  ┌──────────────────────────────────────────────────┐
      │      │  │  DELETE FROM daily_question WHERE member_id = ?  │
      │      │  │                                                  │
      │      │  │  Coordinator: "DOP=4로 실행"                     │
      │      │  │                                                  │
      │      │  │  Slave1 ─┐                                       │
      │      │  │  Slave2 ─┤→ 같은 인덱스 블록 교차 접근           │
      │      │  │  Slave3 ─┤                                       │
      │      │  │  Slave4 ─┘→ ORA-12860                            │
      │      │  └──────────────────────────────────────────────────┘
      │      │
      ├─④ questionCycleService.deleteByMemberId       (실행 안 됨)
      ├─⑤ memberService.withdraw                      (실행 안 됨)
      │
      ▼
[트랜잭션 전체 롤백] → 500 에러 응답
```

### 4-4. 왜 재시도해도 계속 실패했는가?

```
일반 데드락 (ORA-00060)              형제 데드락 (ORA-12860)
─────────────────────────            ─────────────────────────

TX-A vs TX-B 충돌                    단일 DELETE 내부 Slave 간 충돌
→ TX-A victim 선정, 롤백             → DELETE 문장 실패
→ TX-B 성공                          → 트랜잭션 롤백
→ TX-A 재시도 → TX-B 이미 끝남       → 재시도 → 동일 병렬 구조
→ 성공 ✅                            → 동일 인덱스 경합 → 실패 ❌

  재시도하면 성공할 수 있음              재시도해도 구조가 같아서 반복 실패
```

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
→ `NO_PARALLEL`은 SELECT 병렬화 제어용. DML 병렬화에는 효과 없음.
   Oracle ADB의 consumer group 기반 병렬 제어를 힌트로 override 불가.

### ❌ 시도 3: DBMS_CLOUD_ADMIN (ADB 전용 프로시저)

```sql
BEGIN
  DBMS_CLOUD_ADMIN.SET_DATABASE_PARAMETER(
    parameter => 'parallel_degree_policy', value => 'MANUAL'
  );
END;
```

**결과**: `PLS-00302: 'SET_DATABASE_PARAMETER' 구성요소가 정의되어야 합니다`
→ 해당 Oracle ADB 버전에서 프로시저 미지원

### ❌ 시도 4: ALTER SYSTEM (ADMIN 계정으로 OCI Database Actions에서 실행)

```sql
ALTER SYSTEM SET parallel_degree_policy=MANUAL SCOPE=BOTH;
```

**결과**: `ORA-01031: insufficient privileges`
→ Oracle ADB는 ADMIN 계정도 `ALTER SYSTEM` 차단 (완전 관리형 서비스)
→ DB 파라미터 직접 변경 불가능

### ✅ 시도 5: 접속 서비스 변경 `_high` → `_tp`

```
# 변경 전 (ORACLE_DB_URL 환경변수)
jdbc:oracle:thin:@...(service_name=dbname_high)...

# 변경 후
jdbc:oracle:thin:@...(service_name=dbname_tp)...
```

**결과**: 정상 동작 ✅
코드 변경 없이, 환경변수만 수정하여 해결.

---

## 6. 해결 원리

### 변경 전 vs 변경 후

```
 변경 전: _high 서비스
 ═══════════════════════════════════════════════════════════════

  App ──(JDBC)──→ Oracle ADB [_high 서비스]
                        │
                        ▼
                  Consumer Group: HIGH
                  parallel_degree_policy=AUTO 적용
                  parallel_min_degree=CPU (예: 4코어 → DOP=4)
                        │
                        ▼
                  DELETE 실행 → Coordinator + 4 Slaves
                        │
                        ▼
                  Slave들이 인덱스 블록 교차 접근
                        │
                        ▼
                  ⚡ ORA-12860 데드락 ⚡


 변경 후: _tp 서비스
 ═══════════════════════════════════════════════════════════════

  App ──(JDBC)──→ Oracle ADB [_tp 서비스]
                        │
                        ▼
                  Consumer Group: TP
                  OLTP 모드: 병렬 실행 억제
                        │
                        ▼
                  DELETE 실행 → 단일 프로세스 순차 처리
                        │
                        ▼
                  인덱스 블록 순서대로 수정
                        │
                        ▼
                  ✅ 정상 완료
```

> DB 파라미터(`parallel_degree_policy=AUTO`)는 변경되지 않았다.
> **접속 서비스를 바꿔서 "이 세션은 OLTP용"이라고 Oracle에게 알려준 것**이 핵심이다.

---

## 7. MySQL/PostgreSQL과의 비교

이 문제는 **Oracle ADB 특유의 병렬 DML 기능** 때문에 발생한 것으로,
MySQL이나 PostgreSQL에서는 원천적으로 발생하지 않는다.

```
Oracle                          MySQL / PostgreSQL
──────────────────────────      ──────────────────────────
단일 DML을 병렬 실행 가능       단일 DML은 항상 직렬 실행
(Coordinator + Slave 구조)      (단일 프로세스가 순차 처리)

DELETE WHERE member_id=?        DELETE WHERE member_id=?
→ 4개 Slave가 동시 실행 가능    → 1개 프로세스가 순차 실행
→ 인덱스 블록 교차 접근         → 인덱스 순차 접근
→ ORA-12860 가능                → 데드락 불가능
```

Oracle은 원래 대용량 분석/DW까지 커버하는 DB이기 때문에 병렬 DML 기능이 내장되어 있다.
이 기능이 OLTP 워크로드에서는 오히려 독이 될 수 있으며, 이번 장애가 정확히 그 사례다.

---

## 8. 재발 방지 및 추가 권장사항

### 8-1. Oracle ADB 서비스 선택 가이드

| 워크로드 유형 | 권장 서비스 | 이유 |
|--------------|-------------|------|
| API 서버, 앱 서버 (OLTP) | **`_tp`** | 직렬 실행, 낮은 지연시간 |
| 배치 처리, ETL | `_medium` or `_low` | 적절한 병렬, 리소스 제한 |
| 긴급 관리 쿼리 | `_tpurgent` | TP 중 높은 우선순위 |
| DW 분석, 대량 보고서 | `_high` | 최대 병렬, 높은 처리량 |

> **`_high`는 API 서버에서 사용하면 안 된다.**
> OLTP 특성상 짧은 트랜잭션이 많은데 과도한 병렬화가 오히려 성능과 안정성을 해친다.

### 8-2. 현재 쿼리 힌트 정리

해결 과정에서 아래 힌트가 추가됐으나, `_tp` 서비스로 해결됐으므로 **원복해도 무방**하다.

```java
// 원복 (JPQL) ← 권장
@Query("DELETE FROM DailyQuestion dq WHERE dq.member.id = :memberId")
```

---

## 9. 요약

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  근본 원인                                                      │
│  ─────────                                                      │
│  Oracle ADB를 _high 서비스(DW용)로 접속하여 API 서버 운영      │
│  → HIGH consumer group + parallel_degree_policy=AUTO            │
│  → DELETE 문장이 다중 Slave로 병렬 실행                        │
│  → Slave들이 UK 인덱스 블록을 교차 접근하며 ORA-12860 데드락   │
│                                                                 │
│  해결                                                           │
│  ─────                                                          │
│  접속 서비스를 _high → _tp로 변경 (환경변수 수정, 코드 변경 없음)│
│  → TP consumer group = OLTP 모드 = 직렬 실행                   │
│  → Slave 없음 = sibling 없음 = ORA-12860 원천 불가             │
│                                                                 │
│  교훈                                                           │
│  ─────                                                          │
│  Oracle ADB의 서비스 선택은 단순 성능 옵션이 아니라             │
│  DB 내부 실행 방식(병렬/직렬)을 결정하는 핵심 설정이다.         │
│  OLTP 앱은 반드시 _tp 서비스를 사용해야 한다.                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```
