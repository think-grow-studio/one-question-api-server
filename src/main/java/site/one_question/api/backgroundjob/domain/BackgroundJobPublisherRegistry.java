package site.one_question.api.backgroundjob.domain;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code BackgroundJobType} → {@link BackgroundJobPublisher} 매핑.
 * Spring이 주입한 Publisher 목록으로 맵을 만들되, 프레임워크가 알 수 없는 도메인 계약을
 * <b>시작 시점에 fail-fast로 검증</b>한다: (1) 한 타입에 Publisher 중복 금지,
 * (2) 모든 {@code BackgroundJobType}에 Publisher 존재. 덕분에 {@link #get}은 null을 반환하지 않고,
 * "새 타입 추가하고 Publisher 깜빡" 같은 실수가 런타임 NPE 대신 컨텍스트 로딩에서 드러난다.
 */
@Component
public class BackgroundJobPublisherRegistry {

    private final Map<BackgroundJobType, BackgroundJobPublisher> publishers;

    public BackgroundJobPublisherRegistry(Collection<BackgroundJobPublisher> publishers) {
        EnumMap<BackgroundJobType, BackgroundJobPublisher> byType =
                new EnumMap<>(BackgroundJobType.class);
        for (BackgroundJobPublisher publisher : publishers) {
            BackgroundJobType jobType = publisher.supportedType();
            // putIfAbsent가 기존 값을 반환(≠null) → 같은 타입을 지원하는 Publisher가 둘 이상
            if (byType.putIfAbsent(jobType, publisher) != null) {
                throw new IllegalStateException(
                        "BackgroundJobPublisher가 중복 등록되었습니다: jobType=" + jobType);
            }
        }

        // 전체 enum - 등록된 타입 = 담당 Publisher가 없는 타입
        EnumSet<BackgroundJobType> missingTypes = EnumSet.allOf(BackgroundJobType.class);
        missingTypes.removeAll(byType.keySet());
        if (!missingTypes.isEmpty()) {
            throw new IllegalStateException(
                    "BackgroundJobPublisher가 등록되지 않았습니다: jobTypes=" + missingTypes);
        }
        this.publishers = Map.copyOf(byType);
    }

    public BackgroundJobPublisher get(BackgroundJobType jobType) {
        return publishers.get(jobType);
    }
}
