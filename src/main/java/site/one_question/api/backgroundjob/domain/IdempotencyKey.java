package site.one_question.api.backgroundjob.domain;

import site.one_question.api.backgroundjob.domain.exception.BackgroundJobIdempotencyKeyInvalidException;

public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 100;

    public IdempotencyKey {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank() || normalized.length() > MAX_LENGTH) {
            throw new BackgroundJobIdempotencyKeyInvalidException();
        }
        value = normalized;
    }
}
