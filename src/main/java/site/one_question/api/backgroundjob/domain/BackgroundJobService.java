package site.one_question.api.backgroundjob.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BackgroundJobService {

    private final BackgroundJobRepository backgroundJobRepository;

    public BackgroundJob save(BackgroundJob backgroundJob) {
        return backgroundJobRepository.save(backgroundJob);
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
