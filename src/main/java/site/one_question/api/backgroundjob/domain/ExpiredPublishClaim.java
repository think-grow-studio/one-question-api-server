package site.one_question.api.backgroundjob.domain;

public record ExpiredPublishClaim(
        Long id,
        BackgroundJobType jobType,
        String claimId,
        int retryCount
) {
}
