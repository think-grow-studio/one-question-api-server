package site.one_question.api.backgroundjob.domain;

public enum BackgroundJobStatus {
    PENDING,
    PUBLISHING,
    QUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED
}
