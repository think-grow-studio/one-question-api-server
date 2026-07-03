package site.one_question.api.backgroundjob.domain;

public enum BackgroundJobStatus {
    PENDING,
    ENQUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED
}
