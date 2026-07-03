package site.one_question.api.backgroundjob.domain;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BackgroundJobService {

    private final BackgroundJobRepository backgroundJobRepository;

    public BackgroundJob save(BackgroundJob backgroundJob) {
        return backgroundJobRepository.save(backgroundJob);
    }

    public Optional<BackgroundJob> findByIdempotencyKey(
            Long memberId,
            BackgroundJobType jobType,
            IdempotencyKey idempotencyKey
    ) {
        return backgroundJobRepository.findByMemberIdAndJobTypeAndIdempotencyKey(
                memberId, jobType, idempotencyKey.value());
    }
}
