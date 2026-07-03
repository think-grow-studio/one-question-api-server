# One-Question API Server

작업 시작 전 **README.md를 먼저 읽을 것.** 아키텍처, 패키지 구조, 레이어 규칙, 도메인 모델, 컨벤션이 모두 거기에 정리되어 있다.

핵심 규칙 요약 (상세는 README.md):

- 바운디드 컨텍스트별 패키지(`api/<context>/`) + 내부는 presentation → application → domain 레이어. 의존 방향을 역행하지 말 것
- DTO는 record, 엔티티 생성은 정적 팩토리(`create()`), DI는 `@RequiredArgsConstructor`
- 예외는 해당 컨텍스트의 `*ExceptionSpec`에 코드 추가 후 `*Exception` 하위 클래스로 작성
- 스키마 변경은 `ddl-auto` 의존 금지 — `src/main/resources/migration/`에 SQL 추가 (prod는 validate)
- 테스트 규칙은 `src/test/java/site/one_question/CLAUDE.md` 참고 (테스트 파일 작업 시 자동 로드됨)

빌드/테스트: `./gradlew build` / `./gradlew test`, 로컬 실행: `./gradlew bootRun --args='--spring.profiles.active=local'`

## 작업 규칙

1. **되돌리기 어려운 결정은 진행 전에 사용자에게 물어볼 것** — DB 스키마 변경, API 요청/응답 계약 변경, 기존 기능 삭제, 요구사항 해석이 두 갈래 이상으로 갈리는 경우. 그 외 사소한 구현 선택은 기존 컨벤션을 따라 진행한다.
2. **코드 변경 시 관련 문서를 함께 갱신할 것** — 이 파일, README.md, 해당 도메인의 CLAUDE.md에 적힌 내용(불변식, 정책, 구조)이 변경으로 인해 더 이상 사실이 아니게 되면 같은 커밋에서 문서도 수정한다. 문서와 코드가 어긋난 것을 발견하면 사용자에게 알릴 것.
