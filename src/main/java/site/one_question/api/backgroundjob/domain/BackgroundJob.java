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
        uniqueConstraints = @UniqueConstraint(
                name = "uk_background_job_idempotency",
                columnNames = {"member_id", "job_type", "idempotency_key"}
        )
)
public class BackgroundJob extends BaseEntity {

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

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_reason", length = 255)
    private String errorReason;

    public static BackgroundJob create(
            BackgroundJobType jobType,
            Member member,
            String payload,
            String correlationId,
            IdempotencyKey idempotencyKey,
            RequestHash requestHash
    ) {
        return new BackgroundJob(
                null,
                jobType,
                member,
                payload,
                correlationId,
                idempotencyKey.value(),
                requestHash.value(),
                BackgroundJobStatus.PENDING,
                null,
                null,
                Instant.now(),
                null,
                null,
                null,
                0,
                null,
                null
        );
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
