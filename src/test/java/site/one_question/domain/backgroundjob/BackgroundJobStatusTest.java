package site.one_question.domain.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import site.one_question.api.backgroundjob.domain.BackgroundJobStatus;

class BackgroundJobStatusTest {

    @Test
    void queued_is_the_only_queue_waiting_status() {
        assertThat(BackgroundJobStatus.valueOf("QUEUED").name()).isEqualTo("QUEUED");
        assertThatThrownBy(() -> BackgroundJobStatus.valueOf("ENQUEUED"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
