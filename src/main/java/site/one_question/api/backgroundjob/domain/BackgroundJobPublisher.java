package site.one_question.api.backgroundjob.domain;

public interface BackgroundJobPublisher {

    BackgroundJobType supportedType();

    void publish(ClaimedBackgroundJob job);

    default void onPublishExhausted(Long jobId, Exception cause) {
    }
}
