# nginx conf가 재배포해도 안 바뀌던 문제 — bind mount inode 고정 — 기록

> **핵심 한 줄**
> Docker의 **파일 단위 bind mount**는 마운트 시점의 **inode**를 그대로 붙잡는다.
> CI가 파일을 갱신할 때 쓰는 `scp`/`tar` 추출은 파일을 "제자리 수정"하지 않고
> **새 inode로 통째로 교체**하기 때문에, 호스트에서 보면 최신 내용인데
> **컨테이너 안에서는 최초 마운트 시점 내용에 영원히 박제**된다. `nginx -s reload`로도 안 고쳐지고,
> **디렉토리 단위 마운트**로 바꿔야 근본적으로 해결된다.

---

## 1. 증상

`nginx/prod.conf`를 여러 번 고치고, 매 배포마다 `sed`로 sub VM IP까지 정확히 치환해서
main VM에 SCP로 올렸다. 배포 로그도 전부 성공(`nginx -t` 통과, `nginx -s reload` 성공)이었는데,
정작 nginx는 **최초 배포 때의 구버전 설정**으로 계속 동작했다 — 새로 추가한 sub 업스트림이
전혀 반영되지 않아 트래픽이 항상 main으로만 갔다.

```bash
# 컨테이너 안에서 본 설정 — 옛날 내용 (placeholder가 그대로 있음)
$ docker exec one-question-nginx cat /etc/nginx/conf.d/default.conf
upstream spring_backend {
    server app:8080 max_fails=2 fail_timeout=10s;
    # server <SUB_VM_PRIVATE_IP>:8080 max_fails=2 fail_timeout=10s;   ← 주석 처리된 옛날 버전
}
```

```bash
# 같은 시각, 호스트에서 직접 본 파일 — 최신 내용 (IP까지 정확히 치환됨)
$ cat /home/ubuntu/nginx/prod.conf
upstream spring_backend {
    server app:8080 max_fails=2 fail_timeout=10s;
    server 10.0.0.31:8080 max_fails=2 fail_timeout=10s;   ← 최신, 정상
}
$ stat /home/ubuntu/nginx/prod.conf
Modify: 2026-07-02 12:05:40   ← 방금 배포에서 갱신된 시각
```

**같은 경로(`/home/ubuntu/nginx/prod.conf` → `/etc/nginx/conf.d/default.conf`)인데,
호스트와 컨테이너가 보는 내용이 다르다.** 이게 이 조사의 출발점.

---

## 2. 먼저 배제한 것들 (오답 가설)

원인을 좁혀가며 아래를 순서대로 확인하고 하나씩 지웠다.

| 가설 | 확인 방법 | 결과 |
|---|---|---|
| main→sub 네트워크 자체가 막혀있다 | main VM에서 sub private IP로 직접 curl | 정상 응답, 배제 |
| nginx 컨테이너 안에서는 sub한테 못 간다 (Docker 네트워크 네임스페이스 차이) | `docker exec ... wget` | 정상 응답, 배제 |
| GitHub Actions가 옛날 브랜치/커밋을 체크아웃한다 | `git log`, `gh run view`로 커밋 SHA 대조 | 최신 커밋 맞음, 배제 |
| `scp-action`의 `overwrite: false` 기본값이 파일 갱신을 막고 있다 | 로그에서 `overwrite: false` 확인 → `true`로 수정 후 재배포 | 이건 실제로 있던 버그였지만, 고친 뒤에도 증상 재현 → **진짜 원인 아님(부분 원인)** |
| **호스트 파일 자체가 최신인가** | `cat`/`stat` **직접(컨테이너 안 아니고 VM에서)** | ✅ **최신 맞음** → 문제는 호스트→컨테이너 사이 |

마지막 줄에서 병목이 좁혀졌다: **파일은 맞게 갱신됐는데, 컨테이너가 그걸 못 보고 있다.**
이건 애플리케이션(nginx)이나 배포 스크립트 문제가 아니라 **Linux 파일시스템 레벨** 문제라는 뜻.

---

## 3. 핵심 기술 원리: inode, 그리고 "제자리 수정" vs "교체"

### 3.1 파일 하나 = 이름(디렉토리 엔트리) + inode(실체), 이 둘은 분리되어 있다

리눅스에서 파일 경로(`/home/ubuntu/nginx/prod.conf`)는 파일 그 자체가 아니라
**디렉토리 안의 "이름표"** 다. 실제 데이터와 메타데이터는 **inode**라는 별도 구조체에 있고,
디렉토리 엔트리는 그 inode를 가리키는 포인터일 뿐이다.

```
디렉토리 엔트리(이름)        inode(실체)
"prod.conf"        ───────▶  inode #547745  [파일 내용, 권한, 크기...]
```

### 3.2 파일을 "고친다"는 것의 두 가지 방식

- **제자리 수정(in-place write)**: 기존 inode를 열어서 내용만 바꿔 씀. 이름표는 그대로,
  inode 내용물만 바뀜.
- **교체(replace-via-rename)**: 새 파일(= 새 inode)을 임시 이름으로 만들고, 다 쓴 다음
  `rename()`으로 기존 이름표를 새 inode 쪽으로 옮겨 붙임. 기존 inode는 이름표를 잃고 버려짐(unlink).

  ```
  1) 임시 파일 생성       tmpfile ──▶ inode #999999 (새 내용)
  2) rename(tmp, prod.conf)
     "prod.conf" 이름표를 #547745 → #999999 로 재배정
     (#547745는 이제 아무도 안 가리킴 → 결국 회수됨)
  ```

  `tar` 압축 해제, `mv`, 대부분의 텍스트 에디터, 그리고 이번에 쓴 `scp-action`(내부적으로
  tar로 묶어 올린 뒤 서버에서 풀어냄)이 전부 **이 방식**을 쓴다. 이유는 **원자성(atomicity)**
  때문 — 쓰다가 중간에 끊겨도 절반짜리 파일이 그 이름으로 노출되는 일이 없다.

### 3.3 Docker의 "파일 단위 bind mount"는 무엇에 묶이는가

`docker-compose.yml`에 이렇게 쓰면:

```yaml
volumes:
  - /home/ubuntu/nginx/prod.conf:/etc/nginx/conf.d/default.conf:ro
```

컨테이너가 **생성되는 시점**에, 커널은 호스트의 `/home/ubuntu/nginx/prod.conf`가 가리키던
**그 순간의 inode(#547745)** 를 컨테이너 안 `/etc/nginx/conf.d/default.conf` 경로에
직접 이어붙인다(`mount --bind`). 이후 컨테이너 안에서 이 경로를 열면 **항상 그 inode**를 읽는다
— "이름"이 아니라 "그 실체"에 묶인 것이다.

그런데 호스트에서 §3.2의 "교체" 방식으로 파일을 갱신하면, `/home/ubuntu/nginx/prod.conf`라는
**이름표만** 새 inode(#999999)로 옮겨간다. 컨테이너의 bind mount는 처음에 이어붙인
**#547745를 계속 붙잡고 있으므로**, 이름은 같아도 실체는 완전히 다른 옛날 파일을 계속 보게 된다.

```
호스트 관점                          컨테이너 관점 (bind mount)
"prod.conf" → #999999 (최신)         파일 디스크립터가 직접 #547745를 가리킴 (옛날)
                                      → 이름표가 바뀐 걸 알 방법이 없음
```

**이게 정확히 관찰된 증상이다.** 호스트는 최신, 컨테이너는 최초 배포 시점에 박제.

### 3.4 왜 `nginx -s reload`로도 안 고쳐졌나

`nginx -s reload`는 nginx 프로세스한테 "설정 파일을 다시 읽어라"는 신호를 보낼 뿐,
**그 설정 파일이 위치한 경로**(`/etc/nginx/conf.d/default.conf`)를 다시 여는 것 자체는
정상 동작한다. 문제는 그 경로가 **컨테이너의 마운트 네임스페이스 안에서 이미 옛날 inode에
고정**되어 있다는 것 — nginx가 아무리 "다시 읽어도" 커널이 그 경로를 옛날 파일로
연결해주고 있으니 소용이 없다. reload는 애플리케이션 레벨 문제를 고치는 도구고,
이건 그보다 아래(마운트) 레벨 문제였다.

### 3.5 왜 디렉토리 단위 마운트는 이 문제가 없나

```yaml
volumes:
  - /home/ubuntu/nginx:/etc/nginx/conf.d:ro
```

이번엔 **디렉토리의 inode**가 통째로 이어붙는다. 컨테이너 안에서 `/etc/nginx/conf.d/prod.conf`를
열면, 그때마다 커널이 **그 디렉토리 안에서 "prod.conf"라는 이름을 다시 조회**한다 — 즉
파일을 여는 매 순간 "지금 이 이름이 가리키는 inode가 뭐지?"를 다시 찾는다. 호스트에서
이름표가 최신 inode로 옮겨갔다면, 그 다음 조회부터는 바로 새 inode를 찾아낸다.
**"이름에 묶이느냐, 실체에 묶이느냐"의 차이**가 핵심이다.

---

## 4. 조치

`docker-compose.prod.yml`, `docker-compose.dev.yml`의 nginx 서비스 volume을 파일 단위에서
디렉토리 단위로 변경:

```diff
- - /home/ubuntu/nginx/prod.conf:/etc/nginx/conf.d/default.conf:ro
+ - /home/ubuntu/nginx:/etc/nginx/conf.d:ro
```

nginx의 기본 `nginx.conf`는 `include /etc/nginx/conf.d/*.conf;`로 그 디렉토리 안의 모든
`*.conf`를 자동 include하므로, 파일 이름이 `default.conf`가 아니라 `prod.conf`여도
그대로 인식된다 — 별도 이름 변경 불필요.

volume 정의 자체가 바뀌었으므로, `docker compose up -d`가 다음 배포부터 이 변경을 감지해서
**nginx 컨테이너를 자동으로 재생성**한다 (기존 stale bind mount를 버리고 새로 마운트).
그 이후부터는 `nginx -s reload`만으로도 최신 conf가 정상 반영된다.

---

## 5. 더 넓은 교훈

이건 nginx나 이 프로젝트에 국한된 문제가 아니라 **"CI/CD가 파일을 replace 방식으로 갱신하고,
그 파일을 컨테이너에 단일 파일로 bind mount하는" 조합이면 언제든 재현되는 일반적인 함정**이다.
같은 근본 원리(단일 파일 마운트의 inode 고정)가 Kubernetes에서 `subPath`로 마운트한
ConfigMap이 업데이트되어도 파드 안에서 반영이 안 되는 유명한 이슈의 원인이기도 하다
(반대로 `subPath` 없이 디렉토리 전체를 마운트하면 kubelet이 심볼릭 링크 스왑 방식으로
갱신해서 정상 반영된다 — 원리는 동일).

**규칙**: CI가 주기적으로 덮어쓰는 설정 파일을 컨테이너에 물릴 때는, 특별한 이유가 없다면
**파일 단위가 아니라 디렉토리 단위로 bind mount**한다.
