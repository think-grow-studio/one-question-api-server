package site.one_question.domain.backgroundjob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisher;
import site.one_question.api.backgroundjob.domain.BackgroundJobPublisherRegistry;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.ClaimedBackgroundJob;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("백그라운드 작업 Publisher Registry 단위 테스트")
class BackgroundJobPublisherRegistryTest {

    @Test
    @DisplayName("동일한 작업 타입을 지원하는 Publisher가 중복되면 생성에 실패한다")
    void create_when_getPublishers_have_duplicate_type_then_throws() {
        BackgroundJobPublisher first = publisher(BackgroundJobType.ANALYSIS_REPORT);
        BackgroundJobPublisher second = publisher(BackgroundJobType.ANALYSIS_REPORT);

        assertThatThrownBy(() -> new BackgroundJobPublisherRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANALYSIS_REPORT");
    }

    @Test
    @DisplayName("지원 Publisher가 누락된 작업 타입이 있으면 생성에 실패한다")
    void create_when_publisher_type_is_missing_then_throws() {
        assertThatThrownBy(() -> new BackgroundJobPublisherRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANALYSIS_REPORT");
    }

    @Test
    @DisplayName("작업 타입으로 등록된 Publisher를 조회한다")
    void get_when_all_types_are_registered_then_returns_publisher() {
        BackgroundJobPublisher publisher = publisher(BackgroundJobType.ANALYSIS_REPORT);

        BackgroundJobPublisherRegistry registry =
                new BackgroundJobPublisherRegistry(List.of(publisher));

        assertThat(registry.get(BackgroundJobType.ANALYSIS_REPORT)).isSameAs(publisher);
    }

    private BackgroundJobPublisher publisher(BackgroundJobType type) {
        return new BackgroundJobPublisher() {
            @Override
            public BackgroundJobType supportedType() {
                return type;
            }

            @Override
            public void publish(ClaimedBackgroundJob job) {
            }
        };
    }
}
