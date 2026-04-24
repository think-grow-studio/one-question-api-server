# JPA 내부 동작 정리

> FCM Token 개발 중 겪은 JPA 내부 동작 이슈를 바탕으로 정리한 학습 문서.

---

## 1. JPA Flush 순서 — DELETE → INSERT 코드를 짜도 INSERT가 먼저 나간다

### 현상

```java
fcmTokenRepository.deleteByMember(member); // (1) 코드상 먼저 삭제
fcmTokenRepository.save(newToken);          // (2) 코드상 나중에 삽입
```

위처럼 작성해도 실제 DB에 나가는 SQL 순서는 반대다.

```sql
INSERT INTO fcm_token (...) VALUES (...);  -- 먼저 실행
DELETE FROM fcm_token WHERE id = ?;        -- 나중에 실행
```

### 이유

JPA(Hibernate)는 flush 시점에 영속성 컨텍스트에 쌓인 변경사항을 아래 **고정된 순서**로 DB에 반영한다.

| 순서 | 작업 |
|------|------|
| 1    | INSERT |
| 2    | UPDATE |
| 3    | DELETE |

이 순서는 코드 작성 순서와 무관하다. Hibernate가 참조 무결성을 지키기 위해 의도적으로 정해둔 순서다.  
`save()`로 엔티티를 영속 상태로 등록하고, `deleteBy*()`로 삭제 예약을 걸어도, flush 시점에는 INSERT → DELETE 순으로 실행된다.

---

## 2. UNIQUE 제약이 있으면 위 순서 때문에 오류가 터진다

### FCM Token의 상황

`fcm_token` 테이블의 `token` 컬럼에 UNIQUE 제약이 걸려 있었다.

```sql
UNIQUE (token)
```

로직 의도: "기존 토큰 삭제 → 새 토큰 저장"  
실제 DB 실행 순서: INSERT(새 토큰) → DELETE(기존 토큰)

새 토큰을 INSERT할 때 기존 토큰이 아직 DB에 남아 있기 때문에 UNIQUE 제약 위반 오류가 발생한다.

```
ERROR: duplicate key value violates unique constraint "fcm_token_token_key"
```

### 해결 — JPQL로 DELETE를 즉시 실행

```java
@Modifying
@Query("DELETE FROM FcmToken f WHERE f.member = :member")
void deleteByMember(@Param("member") Member member);
```

`@Modifying` + `@Query` JPQL은 영속성 컨텍스트의 `ActionQueue`를 거치지 않고, **트랜잭션 내에서 flush 순서를 우회해 DB에 직접 DELETE 쿼리를 날린다**.  
트랜잭션 자체를 벗어나는 게 아니라, Hibernate가 관리하는 INSERT → DELETE 큐 순서를 건너뛰는 것이다.  
덕분에 "DELETE 먼저 → INSERT 나중" 순서를 보장할 수 있다.

> `@Modifying(clearAutomatically = true)`를 함께 쓰면 실행 후 영속성 컨텍스트를 자동으로 초기화해줘서 1차 캐시와 DB 불일치를 방지할 수 있다.

---

## 3. JPA deleteBy 메서드는 memberId로 바로 DELETE하지 않는다

### 착각하기 쉬운 동작

```java
fcmTokenRepository.deleteByMemberId(memberId);
```

이 메서드가 아래처럼 동작할 것 같지만:

```sql
DELETE FROM fcm_token WHERE member_id = ?;  -- 이렇게 안 된다
```

### 실제 동작

JPA의 `deleteBy*()` 파생 메서드는 두 단계로 동작한다.

**1단계 — SELECT로 엔티티 조회**
```sql
SELECT * FROM fcm_token WHERE member_id = ?;
```

**2단계 — 조회된 엔티티의 PK로 각각 DELETE**
```sql
DELETE FROM fcm_token WHERE id = ?;  -- 조회된 엔티티 수만큼 반복 실행
```

### 이유

JPA는 엔티티의 생명주기를 영속성 컨텍스트에서 직접 관리해야 한다.  
`@PreRemove`, `@PostRemove` 같은 생명주기 콜백과 Cascade 전파를 처리하려면 엔티티를 먼저 메모리에 올려야 하기 때문이다.  
따라서 memberId 조건으로 바로 DELETE를 날리는 게 아니라, 반드시 SELECT로 엔티티를 로딩한 뒤 각 엔티티의 id로 DELETE를 실행한다.

### 성능 문제

삭제 대상이 N개라면:
- SELECT 1번
- DELETE N번

→ N+1 문제와 동일한 구조다. 대량 삭제 시 성능이 나빠진다.

JPQL `@Query("DELETE FROM ...")` 또는 `@Modifying`으로 직접 쿼리를 작성하면 단 1번의 DELETE로 처리 가능하다.

---

## 정리

| 상황 | JPA 기본 메서드 | JPQL @Query |
|------|----------------|-------------|
| flush 실행 순서 | INSERT → UPDATE → DELETE (고정) | 호출 즉시 실행 |
| UNIQUE 제약 + 교체 로직 | 오류 발생 가능 | 순서 제어 가능 |
| deleteBy* 동작 | SELECT 후 id로 DELETE (N번) | 조건으로 직접 DELETE (1번) |
| 생명주기 콜백 | 동작함 | 동작 안 함 |
| 1차 캐시 동기화 | 자동 | clearAutomatically = true 필요 |
