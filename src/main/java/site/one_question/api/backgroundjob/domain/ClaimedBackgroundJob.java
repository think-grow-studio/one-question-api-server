package site.one_question.api.backgroundjob.domain;

public record ClaimedBackgroundJob(
        Long id,
        BackgroundJobType jobType,
        Long referenceId,
        String payload,
        String correlationId,
        String claimId,
        int publishAttemptCount
) {
    public static ClaimedBackgroundJob from(BackgroundJob job) {
        return new ClaimedBackgroundJob(
                job.getId(),
                job.getJobType(),
                job.getReferenceId(),
                job.getPayload(),
                job.getCorrelationId(),
                job.getPublishClaimId(),
                job.getPublishAttemptCount()
        );
    }
}
