# notification 도메인 — FCM 토큰 / 질문 리마인드

코드만으로 알기 어려운 불변식과 정책. 구조/클래스 목록은 코드와 루트 README.md 참조.

## 불변식

- **리마인드 매칭은 "HH:mm" 문자열 정확 일치.** 스케줄러가 매분 실행되어, 활성 설정의 distinct 타임존별로 현재 시각을 해당 타임존의 `HH:mm`으로 변환해 `alarmTime`과 문자열 비교한다. 따라서 스케줄러가 1분을 건너뛰면 그 분의 알림은 유실된다(catch-up 없음) — 이 전제를 바꾸는 변경은 사용자 확인 필요.
- **스케줄러는 ShedLock으로 단일 실행 보장** (`lockAtLeastFor/lockAtMostFor = PT55S`, cron `0 * * * * *`). 55초는 "매분 실행 + 중복 방지"의 균형값이므로 cron과 함께만 조정할 것.
- **발송 대상 필터**: `isEnabled = true` + `MemberStatus.ACTIVE`인 회원만. 탈퇴/비활성 회원에게 발송되면 버그다.
- **리마인드 설정(QuestionReminderSetting)은 회원당 1개** (member_id 유니크, upsert 방식). alarmTime과 timezone은 항상 쌍으로 저장·갱신된다.
- **FcmToken은 (member_id, token) 복합 유니크.** 회원당 토큰 1개 정책은 아직 미확정(엔티티의 TODO 참고) — 한 회원에 여러 토큰이 존재할 수 있음을 전제로 코드를 작성할 것.
- **만료 토큰은 발송 시점에 정리.** 발송 중 `FcmTokenExpiredException`이 발생하면 해당 토큰을 별도 트랜잭션으로 삭제한다. 그 외 발송 실패는 삭제하지 않고 로그만 남긴다.
- **알림 문구는 i18n.** `MessageSource` + 회원 locale로 조회하고, locale이 없으면 한국어가 기본값.
- 회원 탈퇴 시 FcmToken은 `deleteByMemberId`로 정리된다 (auth 도메인의 탈퇴 흐름 참조).
