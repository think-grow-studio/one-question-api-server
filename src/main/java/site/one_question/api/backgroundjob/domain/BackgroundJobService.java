package site.one_question.api.backgroundjob.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import site.one_question.api.member.domain.Member;

@Service
@RequiredArgsConstructor
public class BackgroundJobService {

    private final BackgroundJobRepository backgroundJobRepository;

    /** PENDING 작업을 만들어 저장한다. */
    public BackgroundJob createPending(
            BackgroundJobType jobType,
            Member member,
            Long referenceId,
            String payload,
            String correlationId,
            IdempotencyKey idempotencyKey,
            RequestHash requestHash
    ) {
        return backgroundJobRepository.save(BackgroundJob.create(
                jobType,
                member,
                referenceId,
                payload,
                correlationId,
                idempotencyKey,
                requestHash
        ));
    }

    public BackgroundJob findById(Long jobId) {
        return backgroundJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException(
                        "background job not found: " + jobId));
    }

    public Optional<BackgroundJob> findByIdempotencyKey(
            Long memberId,
            BackgroundJobType jobType,
            IdempotencyKey idempotencyKey
    ) {
        return backgroundJobRepository.findByMemberIdAndJobTypeAndIdempotencyKey(
                memberId, jobType, idempotencyKey.value());
    }

    public List<PendingPublishTarget> findPendingPublishTargets(Instant now, int limit) {
        return backgroundJobRepository.findAllPendingPublishTargets(
                now, PageRequest.of(0, limit));
    }

    public List<ExpiredPublishClaim> findExpiredPublishClaims(Instant recoveryNow, int limit) {
        return backgroundJobRepository.findAllExpiredPublishClaims(
                recoveryNow, PageRequest.of(0, limit));
    }

    public Optional<ClaimedBackgroundJob> claimForPublishing(
            Long jobId,
            String claimId,
            Instant claimUntil,
            Instant now
    ) {
        if (backgroundJobRepository.claimForPublishing(jobId, claimId, claimUntil, now) != 1) {
            return Optional.empty();
        }
        return backgroundJobRepository.findById(jobId)
                .filter(job -> claimId.equals(job.getPublishClaimId()))
                .map(ClaimedBackgroundJob::from);
    }

    public int completePublish(Long jobId, String claimId, Instant now) {
        return backgroundJobRepository.completePublish(jobId, claimId, now);
    }

    public int recordPublishFailure(
            Long jobId,
            String claimId,
            BackgroundJobStatus nextStatus,
            int retryCount,
            Instant nextRetryAt,
            Instant finishedAt,
            String errorCode,
            String errorReason,
            Instant now
    ) {
        return backgroundJobRepository.recordPublishFailure(
                jobId,
                claimId,
                nextStatus,
                retryCount,
                nextRetryAt,
                finishedAt,
                errorCode,
                errorReason,
                now);
    }

    public int recoverExpiredPublish(
            Long jobId,
            String observedClaimId,
            Instant recoveryNow,
            BackgroundJobStatus nextStatus,
            int retryCount,
            Instant nextRetryAt,
            Instant finishedAt,
            String errorCode,
            String errorReason,
            Instant now
    ) {
        return backgroundJobRepository.recoverExpiredPublish(
                jobId,
                observedClaimId,
                recoveryNow,
                nextStatus,
                retryCount,
                nextRetryAt,
                finishedAt,
                errorCode,
                errorReason,
                now);
    }
}
