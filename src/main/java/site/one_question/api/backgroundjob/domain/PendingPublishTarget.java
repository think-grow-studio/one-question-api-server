package site.one_question.api.backgroundjob.domain;

/**
 * 발행 대기 중인 BackgroundJob 후보 스냅샷.
 * 전역(전 타입 통합) 조회 결과에서 Job별 담당 Publisher를 {@code jobType}으로 찾기 위해
 * id와 함께 타입을 담는다.
 */
public record PendingPublishTarget(
        Long id,
        BackgroundJobType jobType
) {
}
