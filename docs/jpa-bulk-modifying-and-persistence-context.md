# bulk `@Modifying` JPQL과 영속성 컨텍스트

`@Modifying` UPDATE/DELETE 쿼리(이하 bulk 쿼리)와 JPA 1차 캐시(영속성 컨텍스트)의
상호작용, 그리고 `flushAutomatically` / `clearAutomatically` 플래그의 의미를 정리한다.

`BackgroundJobRepository`의 발행 상태 CAS 쿼리(`claimForPublishing`, `completePublish`,
`recordPublishFailure`, `recoverExpiredPublish`)가 이 패턴의 대표 사례다.

## 핵심 전제 — bulk 쿼리는 DB만 바꾼다

`@Modifying` JPQL UPDATE는 `executeUpdate()`로 **SQL을 DB에 직접 실행**한다. 따라서
**영속성 컨텍스트(1차 캐시)를 우회**한다.

- DB row는 갱신된다.
- 이미 로드된 관리 엔티티는 **메모리에서 갱신되지 않는다** — 옛 상태 그대로 남는다.
- dirty checking, 라이프사이클 콜백(`@PreUpdate`), JPA Auditing(`@LastModifiedDate`)도
  **실행되지 않는다.**

> 그래서 이 CAS 쿼리들은 `updated_at = :now`를 쿼리 안에서 **직접** 세팅한다.
> auditing이 bulk 경로엔 안 걸리기 때문이다.

## 두 플래그 — 방향이 다르다

| 플래그 | 시점 | 하는 일 | 방향 | 기본값 |
|---|---|---|---|---|
| `flushAutomatically` | 쿼리 **전** | 컨텍스트의 pending 변경을 DB로 flush | **캐시 → DB** | `false` |
| `clearAutomatically` | 쿼리 **후** | 컨텍스트를 비워 관리 엔티티를 detach | **캐시 비움** | `false` |

**중요:** 어느 플래그도 *bulk 쿼리의 결과를 캐시로 가져오지 않는다.* bulk update가 바꾼
DB 값은 캐시에 자동 반영되지 않으며, `flush`는 "내 변경을 DB로" 밀어낼 뿐 "DB 변경을
캐시로" 당겨오지 않는다. 그러므로 **flush와 clear는 서로 다른 문제를 풀며 대체 불가**다.

## 왜 필요한가 — stale read

bulk update 이후 **같은 row의 엔티티가 컨텍스트에 남아 있으면**, 그 엔티티는 옛 상태이고
이어지는 `findById`는 그 stale 캐시본을 반환한다.

```
캐시: job(PENDING, claim=null)
flush        → 내 pending 변경만 DB로 (bulk 결과는 캐시에 안 옴)
bulk update  → DB: PUBLISHING, claim='X'  /  캐시 job: 여전히 PENDING, null  ← stale
findById     → 캐시의 stale job 반환                                        ← 버그
```

이 stale을 없애는 건 **오직 `clearAutomatically`** 뿐이다. clear가 엔티티를 detach하면
다음 `findById`가 DB에서 새로 읽어 갱신된 값을 본다.

`BackgroundJobRepository.claimForPublishing`이 정확히 이 구조다 — bulk로
`PENDING → PUBLISHING` + `claim_id`를 세팅한 **직후 `findById`로 재조회**해 스냅샷을
만든다. clear가 없고 엔티티가 캐시에 있었다면, 재조회가 stale(PENDING, null)을 반환해
`claimId` 검증에 실패하고 "선점 성공을 실패로 오판"하게 된다.

## 언제 켜는가 — 규칙과 함정

- `clearAutomatically = true` → **update 이후 영향받은 엔티티를 다시 읽을 때** 켠다.
- `flushAutomatically = true` → **영향받을 엔티티에 미반영 변경이 있을 수 있을 때** 켠다.

**함정:** `clearAutomatically`는 특정 row가 아니라 **컨텍스트 전체**를 비운다. bulk update와
다른 관리 엔티티 작업이 **한 트랜잭션에 섞이면**, clear 이후의 엔티티 수정이
dirty checking에서 빠져 **조용히 유실**될 수 있다.

```java
@Transactional
void mixed() {
    Entity a = repo.findById(1);   // a 관리 상태
    a.setX(10);
    repo.bulkUpdate(...);          // clearAutomatically=true → a가 detach됨
    a.setY(20);                    // detached → 추적 안 됨 → 저장 안 됨 (유실)
}
```

`flushAutomatically`는 상대적으로 순하다(이른 flush 정도, 유실 위험 없음).

## 판단 요약

- **전용 bulk 메서드 + 격리된 짧은 트랜잭션 + 직후 재조회** → 둘 다 `true`가 정답이자 안전.
  (`BackgroundJobRepository`의 CAS 쿼리들이 이 경우다.)
- **bulk update가 다른 엔티티 조작과 섞이는 트랜잭션** → `clearAutomatically`를 무심코 켜면
  유실 위험. 컨텍스트 재사용 여부를 의식하고 켠다.
- "모든 `@Modifying`엔 무조건 둘 다"는 카고컬트. 필요에 근거해 켠다.

## 이 코드베이스에서의 실제

현재 발행 흐름은 **projection 조회 + fresh `REQUIRES_NEW` 컨텍스트**(스케줄러 스레드,
OSIV 없음)라, CAS 실행 시점의 컨텍스트가 비어 있다. 따라서 지금은 `clearAutomatically`가
없어도 `findById`가 DB로 직행해 최신값을 읽는다 — 즉 **현재는 방어적(robustness)**이다.

그러나 엔티티가 같은 컨텍스트에 미리 로드되는 순간(향후 리팩터, 다른 호출자, OSIV 경로)
곧바로 load-bearing이 되므로, **컨텍스트 상태와 무관하게 항상 옳도록 두 플래그를 유지**한다.

---

**한 줄 요약:** bulk `@Modifying`은 항상 DB에 반영되고, 두 플래그는 그 앞뒤로 캐시를 DB와
맞출지의 문제다. `flush`(캐시→DB, 전)와 `clear`(캐시 비움, 후)는 서로 다른 일이며,
bulk 결과를 다음 조회가 보게 하려면 `clear`로 캐시를 비워야 한다.
