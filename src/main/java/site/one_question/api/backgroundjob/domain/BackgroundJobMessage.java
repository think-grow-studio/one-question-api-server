package site.one_question.api.backgroundjob.domain;

/**
 * 워커에게 발행하는 표준 트리거 메시지 (Claim Check 패턴).
 * 메시지는 참조 {@code jobId}와 원 요청 상관관계 {@code correlationId}만 담고, 실제 페이로드는
 * 워커가 {@code jobId}로 {@code background_job} 및 연결 도메인을 DB에서 역조회해 얻는다.
 * (워커가 DB를 공유하는 아키텍처 전제. 특정 타입이 더 담아야 하면 Publisher가 자체
 * 메시지 타입을 써도 된다.)
 */
public record BackgroundJobMessage(Long jobId, String correlationId) {

    /** 선점한 작업 스냅샷에서 표준 트리거 메시지를 만든다. 모든 타입이 공통으로 사용한다. */
    public static BackgroundJobMessage from(ClaimedBackgroundJob job) {
        return new BackgroundJobMessage(job.id(), job.correlationId());
    }
}
