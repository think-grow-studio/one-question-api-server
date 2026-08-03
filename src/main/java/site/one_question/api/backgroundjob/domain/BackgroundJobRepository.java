package site.one_question.api.backgroundjob.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

    Optional<BackgroundJob> findByMemberIdAndJobTypeAndIdempotencyKey(
            Long memberId,
            BackgroundJobType jobType,
            String idempotencyKey
    );

    @Query("""
            SELECT new site.one_question.api.backgroundjob.domain.PendingPublishTarget(
                job.id, job.jobType)
            FROM BackgroundJob job
            WHERE job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PENDING
              AND job.publishScheduledAt <= :now
            ORDER BY job.publishScheduledAt ASC
            """)
    List<PendingPublishTarget> findAllPendingPublishTargets(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            SELECT new site.one_question.api.backgroundjob.domain.ExpiredPublishClaim(
                job.id, job.jobType, job.publishClaimId, job.publishAttemptCount)
            FROM BackgroundJob job
            WHERE job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PUBLISHING
              AND job.publishClaimUntil <= :recoveryNow
            ORDER BY job.publishClaimUntil ASC
            """)
    List<ExpiredPublishClaim> findAllExpiredPublishClaims(
            @Param("recoveryNow") Instant recoveryNow,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BackgroundJob job
            SET job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PUBLISHING,
                job.publishClaimId = :claimId,
                job.publishClaimUntil = :claimUntil,
                job.publishAttemptCount = job.publishAttemptCount + 1,
                job.updatedAt = :now
            WHERE job.id = :id
              AND job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PENDING
              AND job.publishScheduledAt <= :now
            """)
    int claimForPublishing(
            @Param("id") Long id,
            @Param("claimId") String claimId,
            @Param("claimUntil") Instant claimUntil,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BackgroundJob job
            SET job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.QUEUED,
                job.publishClaimId = NULL,
                job.publishClaimUntil = NULL,
                job.updatedAt = :now
            WHERE job.id = :id
              AND job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PUBLISHING
              AND job.publishClaimId = :claimId
            """)
    int completePublish(
            @Param("id") Long id,
            @Param("claimId") String claimId,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BackgroundJob job
            SET job.status = :nextStatus,
                job.publishScheduledAt = COALESCE(:publishScheduledAt, job.publishScheduledAt),
                job.finishedAt = :finishedAt,
                job.errorCode = :errorCode,
                job.errorReason = :errorReason,
                job.publishClaimId = NULL,
                job.publishClaimUntil = NULL,
                job.updatedAt = :now
            WHERE job.id = :id
              AND job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PUBLISHING
              AND job.publishClaimId = :claimId
            """)
    int recordPublishFailure(
            @Param("id") Long id,
            @Param("claimId") String claimId,
            @Param("nextStatus") BackgroundJobStatus nextStatus,
            @Param("publishScheduledAt") Instant publishScheduledAt,
            @Param("finishedAt") Instant finishedAt,
            @Param("errorCode") String errorCode,
            @Param("errorReason") String errorReason,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BackgroundJob job
            SET job.status = :nextStatus,
                job.publishScheduledAt = COALESCE(:publishScheduledAt, job.publishScheduledAt),
                job.finishedAt = :finishedAt,
                job.errorCode = :errorCode,
                job.errorReason = :errorReason,
                job.publishClaimId = NULL,
                job.publishClaimUntil = NULL,
                job.updatedAt = :now
            WHERE job.id = :id
              AND job.status = site.one_question.api.backgroundjob.domain.BackgroundJobStatus.PUBLISHING
              AND job.publishClaimId = :observedClaimId
              AND job.publishClaimUntil <= :recoveryNow
            """)
    int recoverExpiredPublish(
            @Param("id") Long id,
            @Param("observedClaimId") String observedClaimId,
            @Param("recoveryNow") Instant recoveryNow,
            @Param("nextStatus") BackgroundJobStatus nextStatus,
            @Param("publishScheduledAt") Instant publishScheduledAt,
            @Param("finishedAt") Instant finishedAt,
            @Param("errorCode") String errorCode,
            @Param("errorReason") String errorReason,
            @Param("now") Instant now
    );
}
