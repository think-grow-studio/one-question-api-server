# auth 도메인 — 인증/토큰

코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.

## 불변식

- **인증 경로는 3가지**: Google(id token 검증), Apple(authorization code → 토큰 교환 + client secret 동적 생성), Firebase 익명. 각각 `infrastructure/oauth/`의 verifier가 담당한다.
- **RefreshToken은 회원당 1개(단일 세션).** 재로그인/재발급 시 기존 row를 갱신한다(`RefreshTokenService.save`). 다중 기기 동시 로그인은 지원하지 않음 — 이 전제를 깨는 변경은 사용자 확인 필요.
- **리프레시 토큰 재발급은 저장된 토큰과 정확히 일치해야 한다** (`RefreshTokenMismatchException`). 회전(rotation) 방식이므로 재발급 시 저장 토큰도 교체된다. 만료 시 `RefreshTokenExpiredException` → 클라이언트는 재로그인.
- **익명 → 소셜 계정 연동(link) 시 이미 다른 회원에 연결된 소셜 계정이면 거부** (`AccountAlreadyLinkedException`, `*AccountAlreadyExistsException`). 연동 전 확인용 check API가 별도로 존재한다.
- **탈퇴(withdraw)는 회원 관련 데이터(사이클, 토큰 등)를 함께 정리**한다. 새 도메인에 회원 소유 데이터를 추가하면 탈퇴 흐름에도 삭제를 추가할 것.
- 컨트롤러에서 인증 사용자 ID는 `@PrincipalId`로 주입받는다. `SecurityContextHolder` 직접 접근 금지.
