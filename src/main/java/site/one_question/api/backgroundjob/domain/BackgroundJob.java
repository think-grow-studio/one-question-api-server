package site.one_question.api.backgroundjob.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.member.domain.Member;
import site.one_question.api.backgroundjob.domain.exception.BackgroundJobRequestHashConflictException;
import site.one_question.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "background_job",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_background_job_idempotency",
                        columnNames = {"member_id", "job_type", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_background_job_reference",
                        columnNames = {"job_type", "reference_id"}
                )
        }
)
public class BackgroundJob extends BaseEntity {

    /**
     * 도메인 행 밖의 커맨드 파라미터가 없는 job 타입이 쓰는 payload.
     * NULL 을 쓰지 않는 이유는 소비자(Python 워커 포함)에 null 분기가 영구히 생기는 것을 막기 위함이다.
     */
    public static final String EMPTY_PAYLOAD = "{}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 100)
    private BackgroundJobType jobType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 이 커맨드가 대상으로 하는 도메인 애그리거트의 id.
     * 대상 타입은 {@code job_type} 이 결정하는 폴리모픽 참조이므로 FK 를 걸지 않는다.
     * 대상 애그리거트가 없는 job 타입(배치·집계 등)은 NULL 이다.
     * 생성 시점에 확정되는 불변 값이며 이후 갱신하지 않는다.
     */
    @Column(name = "reference_id")
    private Long referenceId;

    /**
     * 이 커맨드의 파라미터 JSON. 도메인 행에 존재하지 않는 값만 담는다.
     * 담을 파라미터가 없으면 빈 객체 {@code "{}"}를 저장한다 — NULL 을 쓰지 않는 이유는
     * 소비자(Python 워커 포함)에 null 분기가 영구히 생기는 것을 막기 위함이다.
     * 생성 시점에 확정되는 불변 값이며 이후 갱신하지 않는다.
     */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;

    /**
     * 이 작업을 만든 원 요청과 로그를 연결하기 위한 correlation id.
     * 요청 문맥이 있으면 그 요청 id, 없으면(배치/시스템 생성) caller가 새로 생성해 넣는다.
     */
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BackgroundJobStatus status;

    // ---------- 발행 단계 (Spring 전용) ----------

    /**
     * 다음 발행 예정 시각. 발행 실패 시 백오프만큼 미뤄 이 컬럼을 갱신한다 —
     * 별도 재시도 시각 컬럼을 두지 않는다(첫 발행과 재발행이 같은 개념이므로).
     * 그래서 발행 대상 조회가 {@code status=PENDING AND publish_scheduled_at <= now} 하나로 끝난다.
     * 클라이언트향 "요청 시각"은 {@code created_at} 이 담는다.
     */
    @Column(name = "publish_scheduled_at", nullable = false)
    private Instant publishScheduledAt;

    /**
     * 현재 메시지 발행 시도의 소유권 식별자.
     * lease 만료 후 과거 발행자가 새 발행자의 상태를 덮어쓰지 못하게 CAS 조건에 사용한다.
     */
    @Column(name = "publish_claim_id", length = 36)
    private String publishClaimId;

    /**
     * 메시지 발행 선점의 만료 시각.
     * 이 시각이 지나면 중단된 PUBLISHING 작업을 재시도 대상으로 복구한다.
     */
    @Column(name = "publish_claim_until")
    private Instant publishClaimUntil;

    /**
     * 발행 시도 횟수(첫 시도 포함, 1부터). 0 은 아직 시도하지 않았다는 뜻이다.
     * 선점 CAS 에서 증가하므로 첫 시도에 성공한 작업도 1 이 된다.
     */
    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    // ---------- 처리 단계 (워커 전용) ----------

    /**
     * 현재 처리 시도의 소유권 식별자. {@code publish_claim_id} 의 처리 단계 대응물이다.
     * 워커만 읽고 쓴다 — 이 서버는 컬럼만 매핑하고 갱신하지 않는다.
     */
    @Column(name = "process_claim_id", length = 36)
    private String processClaimId;

    /**
     * 처리 선점의 만료 시각. 지나면 중단된 PROCESSING 을 다른 워커가 재선점한다.
     * 비교는 DB 시계 기준이라 앱 시계 동기화에 의존하지 않는다.
     */
    @Column(name = "process_claim_until")
    private Instant processClaimUntil;

    /**
     * 처리 시도 횟수. 워커가 SQS {@code ApproximateReceiveCount} 를 그대로 기록한다.
     * 기록 전용 — terminal 판정은 SQS 값으로 하며 이 컬럼을 조건으로 쓰지 않는다.
     */
    @Column(name = "process_attempt_count", nullable = false)
    private int processAttemptCount;

    /**
     * 처리 시작 시각(관측용). 소유권 판단에는 쓰지 않는다 — {@code process_claim_id} 가 담당한다.
     */
    @Column(name = "process_started_at")
    private Instant processStartedAt;

    // ---------- 생명주기 (끝낸 쪽이 기록) ----------

    /**
     * 종료 시각. 발행 terminal 실패와 처리 종료 양쪽에서 찍히는 생명주기 컬럼이라
     * 단계 접두사를 붙이지 않는다.
     */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /** terminal 실패에만 기록한다. 성공 전이 시에는 남기지 않는다. */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** terminal 실패에만 기록한다. */
    @Column(name = "error_reason", length = 255)
    private String errorReason;

    public static BackgroundJob create(
            BackgroundJobType jobType,
            Member member,
            Long referenceId,
            String payload,
            String correlationId,
            IdempotencyKey idempotencyKey,
            RequestHash requestHash
    ) {
        return new BackgroundJob(
                null,
                jobType,
                member,
                referenceId,
                payload,
                correlationId,
                idempotencyKey.value(),
                requestHash.value(),
                BackgroundJobStatus.PENDING,
                Instant.now(),      // publishScheduledAt
                null,               // publishClaimId
                null,               // publishClaimUntil
                0,                  // publishAttemptCount
                null,               // processClaimId
                null,               // processClaimUntil
                0,                  // processAttemptCount
                null,               // processStartedAt
                null,               // finishedAt
                null,               // errorCode
                null                // errorReason
        );
    }

    /**
     * 대상 애그리거트가 필수인 job 타입에서 {@code reference_id} 를 읽는다.
     * 컬럼은 nullable 이고 job 타입별로 필수 여부가 다르므로 DB 가 강제해주지 않는다.
     * NULL 이면 데이터 오염이므로 어느 타입의 어느 job 인지 알 수 있는 예외로 실패시킨다.
     */
    public Long requireReferenceId() {
        if (referenceId == null) {
            throw new IllegalStateException(
                    jobType + " job has no reference_id: " + id);
        }
        return referenceId;
    }

    public void validateSameRequestHash(RequestHash requestHash) {
        if (!this.requestHash.equals(requestHash.value())) {
            throw new BackgroundJobRequestHashConflictException(
                    member.getId(),
                    jobType,
                    idempotencyKey
            );
        }
    }
}
