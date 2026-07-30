package site.one_question.api.backgroundjob.domain;

public record ClaimedBackgroundJob(
        Long id,
        BackgroundJobType jobType,
        String payload,
        String correlationId,
        String claimId,
        int retryCount
) {
    public static ClaimedBackgroundJob from(BackgroundJob job) {
        return new ClaimedBackgroundJob(
                job.getId(),
                job.getJobType(),
                job.getPayload(),
                job.getCorrelationId(),
                job.getPublishClaimId(),
                job.getRetryCount()
        );
    }
}
