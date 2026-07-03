package site.one_question.api.backgroundjob.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {

    Optional<BackgroundJob> findByMemberIdAndJobTypeAndIdempotencyKey(
            Long memberId,
            BackgroundJobType jobType,
            String idempotencyKey
    );
}
