# publicquestion 도메인 — 공개 데일리 질문

코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.

## 불변식

- **개인 질문(question 도메인)과 달리 날짜 경계가 UTC 기준.** 프로비저닝은 `LocalDate.now(ZoneOffset.UTC)`로 동작하며, (questionDate, locale)당 공개 질문 1개가 유니크.
- **프로비저닝은 스케줄러가 7일 버퍼로 선생성** (`PublicDailyQuestionProvisionScheduler` → `PublicDailyQuestionProvisionApplication`). 이미 존재하는 날짜는 건너뛰므로 멱등. ShedLock으로 다중 인스턴스 중복 실행이 방지된다.
- **질문 선택은 최소 사용 횟수 우선.** locale별 ACTIVE 질문 중 사용 횟수(`QuestionNumberUsage`)가 가장 적은 questionNumber들에서 랜덤 선택 — 균등 순환을 보장하기 위한 정책이므로 임의로 바꾸지 말 것.
- **답변은 익명.** 작성 시 `AnonymousNickname.generate(locale)`로 형용사+동물 닉네임을 부여하며, 응답에 회원 식별 정보(memberId, 이름 등)를 절대 노출하지 않는다.
- **답변은 (member, publicDailyQuestion)당 1개** (`PublicDailyQuestionAnswerAlreadyExistsException`). 내용 길이 제한 및 빈 내용 검증이 도메인 예외로 존재한다.
- 현재 프로비저닝 locale은 `ko-KR`만 동작한다 (`LOCALE_KO` 상수). locale 확장 시 이 클래스 수정 필요.
