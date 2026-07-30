package site.one_question.api.backgroundjob.domain;

/**
 * 커맨드 파라미터 JSON.
 *
 * <p>도메인 행에 존재하지 않는 값만 담는다. 담을 파라미터가 없으면 {@link #empty()} 로
 * 빈 객체 {@code "{}"} 를 쓴다 — NULL 을 허용하지 않는 이유는 소비자(Python 워커 포함)에
 * null 분기가 영구히 생기는 것을 막기 위함이다.
 */
public record JobPayload(String value) {

    private static final JobPayload EMPTY = new JobPayload("{}");

    public JobPayload {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "job payload must not be blank — 담을 파라미터가 없으면 JobPayload.empty()");
        }
    }

    /** 커맨드 파라미터가 없는 job 타입이 쓴다. */
    public static JobPayload empty() {
        return EMPTY;
    }
}
