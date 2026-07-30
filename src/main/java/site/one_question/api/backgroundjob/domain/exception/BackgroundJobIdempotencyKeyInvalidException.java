package site.one_question.api.backgroundjob.domain.exception;

import java.util.Map;

public class BackgroundJobIdempotencyKeyInvalidException extends BackgroundJobException {

    /** 클라이언트가 임의 길이 헤더를 보낼 수 있으므로 로그에 남길 길이를 제한한다. */
    private static final int MAX_LOGGED_LENGTH = 120;

    /**
     * @param rawValue 클라이언트가 보낸 원본 헤더 값. null 일 수 있다.
     *                 원인 추적을 위해 로그 context 에 남긴다 — 값이 없으면 어떤 요청이
     *                 왜 거절됐는지 서버 로그만으로 알 수 없다.
     */
    public BackgroundJobIdempotencyKeyInvalidException(String rawValue) {
        super(BackgroundJobExceptionSpec.IDEMPOTENCY_KEY_INVALID, context(rawValue));
    }

    private static Map<String, Object> context(String rawValue) {
        if (rawValue == null) {
            return Map.of("idempotencyKey", "null");
        }
        // 공백만 있는 값도 로그에서 구분되도록 따옴표로 감싼다.
        return Map.of(
                "idempotencyKey", "'" + truncate(rawValue) + "'",
                "length", rawValue.length()
        );
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LOGGED_LENGTH
                ? value
                : value.substring(0, MAX_LOGGED_LENGTH) + "...(truncated)";
    }
}
