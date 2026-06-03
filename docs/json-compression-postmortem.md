# 대용량 JSON 응답 지연 진단 및 gzip 압축 개선 — 기록

> **핵심 한 줄**
> `histories(size=35)` 응답이 **~18–22KB**로 커서 warm 상태에서도 **~700ms대(2 RTT)** 로 느렸다.
> 원인은 payload가 **TCP 초기 윈도우(IW ≈ 14.6KB)를 넘어** 슬로 스타트 2번째 왕복을 요구했기 때문.
> **gzip 압축**으로 on-wire를 **~2.4KB(서버 실측, ~90%↓)** 로 줄여 **IW 밑 → 1 RTT(~485ms, ~37% 단축)**.
> "압축 덕분이 맞는가 / 커넥션 재사용(cwnd)으로 빨라진 건 아닌가"는 RFC 5681 §4.1로 교란 배제.

---

## 0. 배경 / 구성 (측정 환경)

```
client(한국) ══ leg A ══▶ Cloudflare(LA PoP·orange) ══ leg B ══▶ Oracle VM(origin·인도) ──▶ Oracle DB(인도)
                client↔CF                               CF↔origin
```
한국 유저 · 인도 **origin(= Oracle VM, API 서버)** · Cloudflare 프록시(orange). **단일 API 응답**이
warm 기준 ~400ms, 대용량 응답은 ~700ms대로 느려 그 원인 진단에서 출발했다.

> **용어 정의**
> - **origin** = 인도의 **Oracle VM(= API 서버)**. 그 뒤의 Oracle DB도 같은 인도 리전.
> - **leg A** = **client ↔ Cloudflare** (한국↔LA). **leg B** = **Cloudflare ↔ origin** (LA↔인도).
> - 응답 본문은 **origin → CF → client** 방향, 즉 **leg B → leg A** 를 차례로 통과한다. 두 leg는
    >   **독립된 TCP 연결**(각자 cwnd/슬로 스타트)이다.

### 사전 발견 (이 문서의 전제가 되는 사실)
- **Cloudflare가 한국 트래픽을 미국 LA PoP로 라우팅** — 응답 헤더 `cf-ray: …-LAX` 일관 관측.
  한국 ISP 피어링 이슈로 서울(ICN)이 아님. → 한국 유저 요청이 **한국→LA→인도로 지구를 한 바퀴**.
- **구간 RTT (실측/도출):**

| 구간 | RTT | 근거 |
|---|---|---|
| 한국 ↔ 미국(LA) | ~150ms | 클라↔CF TCP 핸드셰이크 = 1 RTT (실측) |
| 미국(LA) ↔ 인도 | ~257ms | warm TTFB − 한국미국 (도출) |
| 한국 ↔ 인도 (CF 경유, 전체) | ~407ms | warm TTFB − 서버처리 |
| **한국 ↔ 인도 (직접, CF 미경유)** | **~109ms** | **VM SSH 연결 minrtt 실측 (`ss -ti`)** |

→ **거리(직접 ~109ms)보다 Cloudflare-LA 우회(+~298ms)가 더 큰 지연 요인.**
(SSH는 origin에 직접 붙고, API는 CF 경유라 우회됨.)
- **CF↔origin = HTTP/1.1** (클라↔CF는 h2). **서버 처리 자체는 ~1~40ms** (네트워크와 무관, 로그로 확인).

### 측정 도구
- **NetworkProbe** — 자체 제작 Expo/Kotlin 네이티브 모듈(OkHttp `EventListener`). DNS/TCP/TLS/TTFB/total
  레이어별 RTT + warm/cold(keep-alive) 측정. (Android·dev 전용)
- **`[API Perf]`** — axios 인터셉터. 실제 앱 요청의 `durationMs` + `decodedBytes`(디코드 후 크기) 로깅.
- **`LoggingNioEndpoint`** (서버) — TCP 연결 수립/종료·동시 연결 수.
- **`ResponseSizeLoggingValve`** (서버) — 원본 vs 압축(on-wire) 응답 크기.

---

## 1. 문제 (관찰)

`GET /api/v1/questions/histories?size=35` 가 warm(커넥션 재사용) 상태에서도 **~700ms대**로 느렸다.
같은 endpoint를 크기별로 보면:

| decodedBytes | durationMs | |
|---|---|---|
| 8.9 KB | 536 ms | |
| 10.7 KB | 456 ms | |
| **18.4 KB** | **768 ms** | |
| **19.8 KB** | **785 ms** | |

- **~14KB 부근을 경계로 ~300ms 점프** — payload가 어떤 임계를 넘으면 응답에 **왕복(RTT)이 하나 더** 붙는 패턴.
- 서버 처리는 ~수십 ms(로그)라 **이 점프는 네트워크 쪽**. payload 크기가 RTT를 추가시키는 메커니즘을 의심.

## 2. 원인 가설 — TCP 슬로 스타트 초기 윈도우(IW)

- TCP는 연결 직후 **IW(초기 윈도우)** 만큼만 ACK 없이 보내고, ACK를 받아야 윈도우를 키운다(슬로 스타트).
- payload가 **IW를 넘으면 첫 RTT에 다 못 보내고 2번째 윈도우(=+1 RTT)** 가 필요 → 응답이 2 RTT.
- 이 링크의 1 RTT(인도 leg) ≈ ~257ms → **관찰된 ~300ms 점프의 정체로 추정.**
- 즉 **payload > IW → +1 RTT.** 그럼 "IW가 정확히 얼마냐"가 관건.

### 왜 슬로 스타트가 필요한가 (이 비용의 이유)
"처음부터 다 보내면 되지 왜 작게 시작하나?"에 대한 답 — 슬로 스타트는 버그가 아니라 **의도된 안전장치**다.

- **연결 시작 시 네트워크 용량을 모른다.** 송신측은 경로의 대역폭·혼잡 상태를 알 수 없다. 받는 쪽이 광고한
  윈도우(receive window)는 "수신 버퍼"일 뿐, **경로가 견디는 양**이 아니다.
- **처음부터 풀스피드로 쏘면 경로를 넘쳐 무너진다.** 중간 라우터/링크 버퍼를 초과 → 패킷 손실 → 재전송 폭증
  → 더 큰 혼잡 → **혼잡 붕괴(congestion collapse)**. 1980년대 인터넷에서 실제로 일어난 문제이고, 이를 막으려
  Van Jacobson이 슬로 스타트/혼잡제어를 도입했다.
- **그래서 "작게 시작해 ACK로 용량을 탐색(probe)"** 한다. IW만큼 보내고, ACK가 돌아오면 "네트워크가 그만큼은
  받아냈다"는 신호로 윈도우를 **매 RTT 2배**로 키운다(=ACK clocking). 손실을 만나면 거기서 멈춰 혼잡회피
  (congestion avoidance, 선형 증가)로 전환 — 그 지점이 대략 경로의 가용 용량.
- **결론**: 첫 몇 RTT가 처리량 제한을 받는 건 **"안전하게 용량을 알아내는 대가"**. 그래서 IW를 넘는 payload는
  필연적으로 추가 RTT를 문다. → 이 비용을 피하려면 **payload를 IW 밑으로(=압축)** 두는 것이 정공법.

## 3. IW 임계 — 공식 근거 (RFC 6928)

```
IW = min (10 * MSS, max (2 * MSS, 14600))
```
- **IW10** = 초기 윈도우 10 세그먼트. 전형적 MSS 1460B → **14,600 bytes ≈ 14.3 KiB**.
  - (본 스택의 공용 인터넷 경로 실측 MSS = **1448B** [`ss -ti`] → IW ≈ **14.48KB**)
- 즉 "첫 RTT에 약 14.6KB까지는 ACK 없이 전송" → 그보다 큰 응답은 **2번째 윈도우 필요 = +1 RTT**.

> ⚠️ **단, "확정 상수"는 아니다.** RFC 6928은 **Experimental** 이고 *"a TCP MAY start with an initial
> window smaller than 10 segments"* (권고·MAY). 값은 **MSS·OS·CDN 의존**(Cloudflare는 더 큰 IW 튜닝 가능,
> RFC 3390 시절엔 ~4KB였음). 하드 기준으로 쓰려면 **스택별 실측 필요** — 그것도 client↔CF·CF↔origin
> 두 구간이 각각 다름.

→ **관찰된 18~22KB > ~14.6KB(IW)** → 2번째 윈도우 → **2 RTT** → §1의 ~700ms와 정합.

## 4. 해결 + 검증 — gzip 압축 A/B

payload를 **IW 밑으로** 줄이면 2번째 윈도우가 사라져 1 RTT로 떨어질 것. gzip으로 검증.

### 적용 구성
- **origin(Spring Boot)에서 gzip 압축 활성화**, 대상 MIME **`application/json` 만**(재압축 낭비 방지).
- 클라(OkHttp): **무설정** — `Accept-Encoding: gzip` 자동 주입 + 투명 해제.

### 결과 (`size=35`, warm)

| 구분 | decodedBytes | durationMs | RTT(추정) |
|---|---|---|---|
| 무압축 | 18.4 KB | 768 ms | 2 |
| 무압축 | 19.8 KB | 785 ms | 2 |
| **gzip** | **22.5 KB** | **508 ms** | **1** |
| **gzip** | **22.5 KB** | **465 ms** | **1** |

- 압축 후에도 `decodedBytes`는 22.5KB(해제 후라 오히려 더 큼)인데 durationMs는 **465~508ms**.
- 무압축이면 22.5KB는 IW 초과라 **반드시 2 RTT(~770ms+)** — 1 RTT(~485ms)로 나온 건 **on-wire가 gzip으로
  줄어 IW 안으로 복귀**했다는 직접 증거.
- **22.5KB(더 큰 payload)가 18KB(더 작은 무압축)보다 ~300ms 빠름** — 크기 역전이 곧 압축 작동의 증거.

```
대형 응답(>14.6KB):  무압축 2 RTT ~770ms  →  gzip 1 RTT ~485ms   ≈ -285ms (~37%)
소형 응답(<14.6KB):  변화 없음 (이미 1 RTT, jitter 범위)
```

### 서버 실측 압축 크기 (ResponseSizeLoggingValve)
origin(Tomcat)에 응답 크기 로깅 Valve를 추가해 **원본 vs 압축(on-wire) 크기를 직접 기록**(2026-06-03):
```
[RESP] GET /api/v1/questions/histories status=200 원본=23626B 전송=2433B(gzip) 절감=89.7%
[RESP] GET /api/v1/questions/histories status=200 원본=22966B 전송=2459B(gzip) 절감=89.3%
```
| 원본(decoded) | 전송(wire·gzip) | 절감 | 배율 |
|---|---|---|---|
| 23,626 B (~23 KB) | **2,433 B (~2.4 KB)** | **89.7%** | ~9.7x |
| 22,966 B (~22 KB) | **2,459 B (~2.4 KB)** | **89.3%** | ~9.3x |

- gzip이 이 JSON을 **~90% 압축(≈10x)** (반복 구조 JSON이라 압축률 매우 높음).
- **결정적**: 압축 후 **~2.4 KB ≪ IW10(~14.6 KB)** → 첫 윈도우 안 = **1 RTT 확정.** §4 앞부분의 durationMs
  추론(2 RTT→1 RTT)을 서버 실측이 직접 뒷받침.
- **여유도 큼**: 2.4 KB vs 14.6 KB → 원본이 **약 6배(~140 KB)** 까지 커져도 압축 후 여전히 IW 안 → 1 RTT 유지.
- 이 Valve는 **origin이 보낸 바이트**를 기록 → **비싼 인도 leg(origin→CF)가 2.4 KB로 건너감** 확인
  (= origin 압축이 올바른 위치였음).

> 참고: 앱 `decodedBytes`(~22.5KB)와 서버 `원본`(~23KB)의 ~1KB 차이는 JSON.stringify(앱) vs
> 서버 raw 응답 바이트(공백·키 순서·숫자 표기 차이) 때문. 둘 다 "압축 전 크기"로 동일 맥락.
> 또한 앱(axios)에선 OkHttp가 gzip 투명 해제하며 `Content-Length`/`Encoding` 헤더를 strip해
> **wire 크기를 못 봄** → 그래서 압축 크기는 **서버 `%b`/Valve**로 측정.

## 5. RTT 횟수·구간 귀속은 "추정"

> ⚠️ **RTT 횟수(2/1)는 직접 측정값이 아니라 추정.** 실측한 것은 `durationMs`·`decodedBytes`·크기별
> 패턴·서버 압축 크기뿐이며, "~300ms 점프 = 슬로 스타트 추가 왕복 1번"은 **durationMs + IW10 모델로
> 추론**한 라벨이다. 실제 왕복 수는 패킷 캡처(tcpdump/Wireshark)로 확정하지 않았다.

### 어느 구간의 RTT가 줄었나 (추정)
줄어든 ~285ms가 **어느 leg의 RTT와 일치하는가**로 귀속을 추론:

| 후보 구간 | RTT | 절감폭(~285ms)과 매칭 |
|---|---|---|
| 한국 ↔ 미국(LA) | ~150ms | ❌ 너무 작음 |
| **미국(LA) ↔ 인도 (origin↔CF)** | **~257ms** | ✅ **거의 일치** |
| 양쪽 합 | ~407ms | ❌ 너무 큼 |

- 절감폭 ~285ms ≈ **LA↔인도 RTT(257ms)** → 줄어든 1 RTT는 **origin↔CF(인도) leg**로 추정.
- **CF↔client(LA) leg는 페널티 없었던 듯** — Cloudflare가 initcwnd를 크게 튜닝하면 18~22KB도 한 윈도우에
  전송되어 추가 RTT가 안 생김(추정).

> **단, "leg A가 1 RTT" ≠ "client가 빠르다" (중요).** CF는 응답을 다 모았다 보내는 store-and-forward가
> 아니라 **받는 대로 흘려보내는 streaming**이다. origin이 슬로 스타트로 데이터를 **2 wave(2 RTT)** 에 걸쳐
> CF에 보내면, CF는 **아직 받지 않은 바이트를 못 보내므로** client도 2 wave로 받는다. 즉 leg A의 윈도우가
> 여유 있어도 **client의 wall-clock은 leg B(origin→CF)의 2 RTT에 종속**된다.
> → 추가 RTT는 **leg B에서 "발생"**, client는 데이터가 origin 슬로 스타트에 막혀 그 시간을 **"기다리는"** 구조.
> (이 streaming 가정 역시 미확정 — CF가 buffering이면 양상이 달라질 수 있음.)

- 이 추론은 **"origin에서 압축해야 효과"** 라는 결론과 정합 — 병목이 인도 leg였으므로 origin이 그 구간에
  압축 바이트를 실어야 함.
- **확정 방법(미실행):** ⓐ "CF만 압축(origin 무압축)"으로 개선이 사라지면 인도 leg 확정, ⓑ leg별 패킷 캡처.

## 6. 교란 배제 — "정말 압축 때문인가" + "무압축 2번째는 왜 안 빨라졌나" (cwnd)

§4 개선이 **압축이 IW 밑으로 줄여 슬로 스타트 RTT를 없앤 것**인지, 아니면 **커넥션 재사용으로 cwnd가
이미 커져 빨랐던 것**(교란)인지 구분이 필요했다. 동시에, 압축 *전* 무압축 큰 요청 2개가 **거의 같은
시간(768 ≈ 785ms)** 으로 carry-over가 없던 것도 설명해야 한다. 두 의문 다 같은 메커니즘으로 풀린다.

### 확인 1 — 커넥션 재사용 여부 (origin TCP 로거)
origin의 `LoggingNioEndpoint`(연결 수립/종료·동시 연결 수)로 관찰:
- **curl 루프**: 매 요청 TCP 수립→종료 (각 curl이 별도 프로세스라 재사용 X).
- **앱(OkHttp)**: TCP 유지·재사용 (동시 연결 수 유지) → **CF↔origin TCP도 살아있음**.
- 부수: 요청 protocol **HTTP/1.1**(CF↔origin 확정), 서버 처리 ~4ms(서버 무관 재확인).

### 확인 2 — "연결 유지 ≠ cwnd 유지" (RFC 5681 §4.1)
RFC 5681 §4.1 "Restarting Idle Connections" 명문:

> *"a TCP SHOULD set cwnd to no more than **RW** before beginning transmission if the TCP has not
> sent data in an interval exceeding the **retransmission timeout**."*  (RW = min(IW, cwnd))

- **idle > RTO** 이면 cwnd를 **RW = min(IW, cwnd) = IW** 로 리셋 (컸어도 IW로 되돌림).
- RTO ≈ 수백 ms~1s (인도 RTT ~257ms 기반, Linux 최소 200ms). 요청 간 텀이 그 이상이면 리셋.
- → **warm 커넥션이어도 cwnd는 매 요청 IW에서 시작**(텀 있으면). Linux: `tcp_slow_start_after_idle`(기본 1).

### 확인 3 — 무압축 2번째 요청이 안 빨라진 이유
- 무압축 큰 요청 연속 → **768 ≈ 785ms**(carry-over 없음).
- 기대: TCP 재사용 + 1번째에서 cwnd 성장 → 2번째는 커진 cwnd로 1 RTT여야.
- 실제: 두 요청 사이 **idle > RTO → §4.1로 cwnd가 IW로 리셋** → 2번째도 슬로 스타트 처음부터 = 또 2 RTT.
- 즉 **"연결은 유지(handshake 절약✓) / cwnd는 리셋(carry-over✗)"** 이 768≈785의 정체. cwnd 성장은
  사실상 **단일 transfer 안에서만** 일어나고 다음 요청으로 안 넘어감.

### 결론 — 인과 확정
- cwnd는 RFC상 **idle마다 IW로 리셋**되어 요청 간 안정적 우위를 못 줌 → **"cwnd 물려받아 빨랐다" 교란 배제**.
- 압축/무압축 차이(~285ms)는 **payload의 on-wire 크기가 IW를 넘느냐**에만 연동, cwnd 상태와 **독립**.
- ∴ §4의 개선은 **압축이 on-wire payload를 IW 밑으로 줄여 슬로 스타트 RTT를 제거한 것**이 맞다.
- 그래서 **압축이 "cwnd 운빨"보다 견고**: payload < IW면 cwnd가 IW로 리셋돼도 항상 1 윈도우.

## 7. 권고 (정리)

지연의 본질은 **payload가 아니라 거리(특히 Cloudflare-LA 우회)**. 레버를 효과/비용순으로:

| 레버 | 효과 | 비용 / 트레이드오프 | 상태 |
|---|---|---|---|
| **gzip 압축** (origin, `application/json`) | 대형(>IW) 응답 2RTT→1RTT (~37%) + 데이터 ~90%↓ | 설정 한 줄 | ✅ 적용 |
| **왕복 수 줄이기** (API aggregation·`Promise.all` 병렬) | 호출당 ~400ms 누적 절감 | API 설계 | 권장 |
| **cold 방지** (앱 foreground 커넥션 워밍) | 첫 요청 ~1100→~400ms | 작은 코드 | 권장 |
| **grey-cloud** (API DNS-only) | CF-LA 우회 제거 → ~407→~110ms | origin IP 노출·WAF/DDoS 상실(Oracle WAF로 대체 가능) | 검토 |
| **origin 서울 이전** (Oracle ap-seoul-1) | 거리 자체 제거 → ~20~50ms | 마이그레이션(Always Free면 home region 제약) | 근본 |

- **즉시 가능**: 압축(적용 완료)·왕복 수 줄이기·cold 방지 — 코드/설정 수준.
- **근본 레버**: grey-cloud / origin 서울 이전 — 보안·마이그레이션 비용을 수반하지만 거리를 실제로 줄이는 유일한 길.
- 압축은 **"싸게 얻는 부분 개선"**, 체감을 바꾸는 건 **origin 위치**.

## 참고 (공식 문서)
- **RFC 6928** — Increasing TCP's Initial Window (IW10, `min(10*MSS, max(2*MSS, 14600))`, Experimental)
  — https://www.rfc-editor.org/rfc/rfc6928
- **RFC 5681** — TCP Congestion Control (슬로 스타트 일반 + **§4.1 Restarting Idle Connections**: idle>RTO 시 cwnd→RW=min(IW,cwnd) 리셋)
  — https://www.rfc-editor.org/rfc/rfc5681
- **RFC 2861** — TCP Congestion Window Validation (idle·application-limited 구간 cwnd 감쇠)
  — https://www.rfc-editor.org/rfc/rfc2861
- **RFC 6298** — Computing TCP's Retransmission Timer (RTO 계산: SRTT + 4×RTTVAR)
  — https://www.rfc-editor.org/rfc/rfc6298
- **RFC 3390** — (역사적) Increasing TCP's Initial Window (이전 IW ~3–4 세그먼트)
  — https://www.rfc-editor.org/rfc/rfc3390
