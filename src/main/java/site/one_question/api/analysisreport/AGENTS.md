# analysisreport 도메인 — AI 분석 리포트

이 컨텍스트를 수정할 때는 루트 `README.md`의 레이어 규칙을 따른다.

## 불변식

- 리포트 생성 요청은 **본인의 개인 DailyQuestionAnswer 10~15개**만 소스로 받을 수 있다.
- 지원하는 리포트 타입은 `THINKING_PATTERN`(사고 패턴 분석), `WARM_REFLECTION`(따듯한 회고 편지)이다.
- 소스 답변 ID의 개수/중복 검증은 `AnalysisReportSourceAnswerIds`가 담당한다. 중복이 있으면 리포트와 백그라운드 작업을 만들지 않는다. 요청의 ID 순서는 의미가 없다.
- 요청 ID와 조회 결과의 개수 대조(누락·비소유 탐지)는 application이 수행한다. `AnalysisReportSourceService`는 답변 소유권을 직접 검증(`isOwnedBy`)한 뒤 소스 스냅샷을 생성한다. `seq_no`는 질문 날짜(`question_date`) 내림차순으로 부여한다 — 1번이 가장 최신.
- 리포트 생성 API는 한 트랜잭션에서 `background_job(PENDING)`, `analysis_report`, `analysis_report_source`를 함께 생성한다.
- `analysis_report_source`는 요청 시점의 질문/답변 내용을 스냅샷으로 저장한다. 이후 답변이 수정돼도 이미 생성된 리포트 소스는 바꾸지 않는다.
- 클라이언트는 생성 요청마다 `Idempotency-Key` 헤더를 보낸다. 같은 생성 의도를 재시도할 때만 같은 키를 재사용한다.
- 멱등성 범위는 `(memberId, jobType, idempotencyKey)`이다. 같은 사용자가 같은 작업 타입을 여러 번 생성하려면 매번 새 멱등키를 사용한다.
- 같은 멱등키와 같은 요청 payload는 기존 `background_job`/`analysis_report` 응답으로 수렴한다.
- 같은 멱등키로 다른 요청 payload를 보내면 `BACKGROUND-JOB-003`으로 거절한다.
- `background_job.request_hash`는 멱등키 재사용 시 payload 동일성 검증용이며, dedupe 기준이 아니다.
- `background_job.job_data`에는 `memberId`, `reportType`만 JSON 문자열로 저장하며, 생성 시점에 확정된 후 갱신하지 않는다. 소스 답변 목록은 `analysis_report_source`가 원천이므로 job_data에 중복 저장하지 않는다.
- `analysis_report.status`(PENDING/COMPLETED/FAILED)가 리포트 라이프사이클을 소유한다. 전이는 엔티티 메서드(`complete`/`fail`)로만 하며, PENDING에서만 전이할 수 있다.
- `result`, `provider`, `model`, `llm_options`는 `complete()`가 COMPLETED 전이와 함께 원자적으로 채운다. COMPLETED가 아니면 항상 NULL이다.
- `background_job.status`는 큐 발행/처리 관점의 운영 상태이고, 클라이언트향 리포트 상태는 `analysis_report.status`를 본다. 워커는 처리 결과를 두 상태 모두에 반영해야 한다.
- 공통 `BackgroundJobPublishScheduler`가 기본 5초마다 발행 대기 작업을 전 타입 통합 조회해 각 작업 타입에 맞는 Publisher로 발행한다. dev/prod에서는 `app.background-job.publisher.enabled=true`, local/test에서는 false다.
- `AnalysisReportJobPublisher`는 `ANALYSIS_REPORT` 작업의 메시지 조립과 분석 리포트 전용 SQS 큐 선택을 담당한다.
- SQS 메시지 body는 참조용으로 `jobId`, `correlationId`만 담는다(Claim Check 패턴, 공통 `BackgroundJobMessage`). Worker는 `jobId`로 `background_job`과 연결된 `analysis_report`(`background_job_id` 1:1 유니크)를 역조회해 `analysisReportId`·`reportType` 등 나머지를 DB에서 얻는다. 발행은 at-least-once — Worker는 `jobId` 상태 CAS로 중복 수신을 멱등 처리하고, 리포트 상태 가드(AI-REPORT-004)는 순차 중복 완료를 막는 2차 방어다.
- Python Worker는 조기 수신 시 `background_job`이 아직 `PENDING` 또는 `PUBLISHING`이면 메시지를 acknowledge/delete하지 않고 visibility timeout 후 재전달되게 둔다. `QUEUED → PROCESSING` CAS 성공자만 분석을 실행하며, durable한 `PROCESSING`/`SUCCEEDED`/`FAILED` 결정을 확인한 중복 수신 또는 처리 결과를 durable하게 기록한 실행만 acknowledge/delete한다.
- 워커의 분석 입력 원천은 `analysis_report_source` 스냅샷이다 — `jobId`로 역조회한 `analysis_report`의 id로 `seq_no` 순 조회하며, answer/question/member 원본 테이블은 조인하지 않는다.
- 작업이 정상 발행되면 `background_job.status=QUEUED`가 된다. 발행 재시도 소진으로 FAILED가 되면 리포트도 별도 짧은 트랜잭션에서 `fail()`로 전이한다.
