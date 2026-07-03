package site.one_question.integrate.test_config.utils;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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
        Member member = testMemberUtils.createSave();
        BackgroundJob job = BackgroundJob.create(
                BackgroundJobType.ANALYSIS_REPORT,
                member,
                "{}",
                UUID.randomUUID().toString(),
                new IdempotencyKey(UUID.randomUUID().toString()),
                RequestHash.sha256("{}")
        );
        return repository.save(job);
    }
}
