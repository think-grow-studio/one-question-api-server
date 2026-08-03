# question 도메인 — 개인 데일리 질문

코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.

## 불변식

- **날짜 경계는 클라이언트 타임존 기준.** 모든 날짜 판단은 요청 헤더의 타임존으로 계산한다 (`DatePolicy.getToday(timezone)`). 서버 시간(`LocalDate.now()`)을 직접 쓰지 말 것.
- **미래 날짜 질문 조회 금지** (`FutureDateQuestionException`), **가입일(첫 사이클 시작일) 이전 날짜 조회 금지** (`BeforeSignupDateException`).
- **사이클(QuestionCycle)은 1년 단위, memberId + cycleNumber 유니크.** 최신 사이클 이후 날짜가 요청되면 해당 날짜를 포함할 때까지 다음 사이클을 연쇄 생성한다 (`QuestionCycleService.getOrCreateCycle`).
- **질문 서빙은 멱등.** 같은 (member, date)에 이미 DailyQuestion이 있으면 새로 만들지 않고 그대로 반환한다.
- **답변이 달린 DailyQuestion은 질문 변경(리로드/선택) 불가** (`AnswerAlreadyExistsException`).
- **리로드 상한은 회원 권한별** (`MemberPermission.getMaxQuestionChangeCount()`). 리로드마다 changeCount 증가, 초과 시 `ReloadLimitExceededException`.
- **리로드 시 새 질문은 기존 후보(DailyQuestionCandidate)를 제외하고 선택**하며, 후보는 order 순번으로 누적 저장된다. 질문 선택(select)은 반드시 후보 목록 안에서만 가능.
- **답변은 (member, dailyQuestion)당 1개.** 수정은 가능하나 중복 생성은 `AnswerAlreadyExistsException`.
