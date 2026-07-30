package site.one_question.integrate.test_config.utils;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
import site.one_question.api.backgroundjob.domain.BackgroundJobRepository;
import site.one_question.api.backgroundjob.domain.BackgroundJobType;
import site.one_question.api.backgroundjob.domain.IdempotencyKey;
import site.one_question.api.backgroundjob.domain.RequestHash;
import site.one_question.api.member.domain.Member;

@Component
@RequiredArgsConstructor
public class TestBackgroundJobUtils {

    private final BackgroundJobRepository repository;
    private final TestMemberUtils testMemberUtils;

    public BackgroundJob createSave() {
        return createSave(testMemberUtils.createSave());
    }

    public BackgroundJob createSave(Member member) {
        return createSave_With_Reference(member, null);
    }

    public BackgroundJob createSave_With_Reference(Member member, Long referenceId) {
        BackgroundJob job = BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                referenceId,
                BackgroundJob.EMPTY_PAYLOAD,
                UUID.randomUUID().toString(),
                new IdempotencyKey(UUID.randomUUID().toString()),
                RequestHash.sha256("{}")
        );
        return repository.save(job);
    }

    /**
     * 이미 발행에 {@code count}회 실패한 이력이 있는 PENDING 작업 상태를 시딩한다.
     * 프로덕션 흐름은 repository bulk CAS로 실패를 기록하므로 엔티티에 셋터가 없어,
     * 테스트 셋업 목적으로만 내부 상태를 직접 채운다.
     */
    public BackgroundJob recordPreviousPublishFailures(
            BackgroundJob job,
            int count,
            Instant nextRetryAt
    ) {
        ReflectionTestUtils.setField(job, "retryCount", count);
        ReflectionTestUtils.setField(job, "errorCode", "PUBLISH_FAILED");
        ReflectionTestUtils.setField(job, "errorReason", "previous");
        ReflectionTestUtils.setField(job, "nextRetryAt", nextRetryAt);
        return repository.saveAndFlush(job);
    }
}
