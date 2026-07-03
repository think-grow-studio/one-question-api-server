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

    @Lob
    @Column(name = "job_data", nullable = false, columnDefinition = "CLOB")
    private String jobData;

    @Column(name = "trace_id", nullable = false, length = 100)
    private String traceId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BackgroundJobStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

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
            String jobData,
            String traceId,
            IdempotencyKey idempotencyKey,
            RequestHash requestHash
    ) {
        return new BackgroundJob(
                null,
                jobType,
                member,
                jobData,
                traceId,
                idempotencyKey.value(),
                requestHash.value(),
                BackgroundJobStatus.PENDING,
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
