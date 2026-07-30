package site.one_question.domain.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.api.backgroundjob.domain.IdempotencyKey;
import site.one_question.api.backgroundjob.domain.exception.BackgroundJobIdempotencyKeyInvalidException;

@DisplayName("IdempotencyKey 검증")
class IdempotencyKeyTest {

    @Test
    @DisplayName("앞뒤 공백을 제거해 보관한다")
    void create_with_padded_value_then_trims() {
        assertThat(new IdempotencyKey("  abc-123  ").value()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("null이면 거절하고 로그에 null임을 남긴다")
    void create_with_null_then_throws_with_null_context() {
        assertThatExceptionOfType(BackgroundJobIdempotencyKeyInvalidException.class)
                .isThrownBy(() -> new IdempotencyKey(null))
                .satisfies(e -> assertThat(e.getLogMessage())
                        .as("서버 로그만으로 헤더가 아예 없었음을 알 수 있어야 함")
                        .contains("BACKGROUND-JOB-001")
                        .contains("idempotencyKey=null"));
    }

    @Test
    @DisplayName("공백뿐이면 거절하고 원본 값을 따옴표로 감싸 남긴다")
    void create_with_blank_then_throws_with_quoted_context() {
        assertThatExceptionOfType(BackgroundJobIdempotencyKeyInvalidException.class)
                .isThrownBy(() -> new IdempotencyKey("   "))
                .satisfies(e -> assertThat(e.getLogMessage())
                        .as("공백만 있는 값도 로그에서 구분되어야 함")
                        .contains("idempotencyKey='   '")
                        .contains("length=3"));
    }

    @Test
    @DisplayName("100자를 넘으면 거절하고 원본 길이를 남긴다")
    void create_with_too_long_value_then_throws_with_length_context() {
        String tooLong = "a".repeat(101);

        assertThatExceptionOfType(BackgroundJobIdempotencyKeyInvalidException.class)
                .isThrownBy(() -> new IdempotencyKey(tooLong))
                .satisfies(e -> assertThat(e.getLogMessage())
                        .as("길이 초과 사유를 로그에서 알 수 있어야 함")
                        .contains("length=101"));
    }

    @Test
    @DisplayName("아주 긴 값은 로그에 잘라서 남긴다")
    void create_with_huge_value_then_truncates_log() {
        String huge = "a".repeat(5000);

        assertThatExceptionOfType(BackgroundJobIdempotencyKeyInvalidException.class)
                .isThrownBy(() -> new IdempotencyKey(huge))
                .satisfies(e -> {
                    assertThat(e.getLogMessage())
                            .as("로그 폭주를 막기 위해 잘라야 함")
                            .contains("...(truncated)")
                            .hasSizeLessThan(300);
                    assertThat(e.getLogMessage())
                            .as("잘려도 원본 길이는 알 수 있어야 함")
                            .contains("length=5000");
                });
    }
}
