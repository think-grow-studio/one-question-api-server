package site.one_question.integrate.backgroundjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.member.domain.Member;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("BackgroundJob reference_id 유니크 제약 통합 테스트")
class BackgroundJobReferenceUniqueIntegrateTest extends IntegrateTest {

    @Test
    @DisplayName("같은 job_type과 reference_id로 두 번 저장하면 유니크 제약을 위반한다")
    void save_job_with_duplicated_reference_then_violates_unique_constraint() {
        // given
        Member member = testMemberUtils.createSave();
        AnalysisReport report = testAnalysisReportUtils.createSave(member);
        testBackgroundJobUtils.createSave_With_Reference(member, report.getId());

        // when & then
        assertThatThrownBy(() ->
                testBackgroundJobUtils.createSave_With_Reference(member, report.getId()))
                .as("리포트 1건당 job 1건이어야 함")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(backgroundJobRepository.findAll())
                .as("제약 위반 시 두 번째 job이 저장되지 않아야 함")
                .hasSize(1);
    }

    @Test
    @DisplayName("reference_id가 NULL인 job은 같은 job_type으로 여러 건 저장할 수 있다")
    void save_multiple_jobs_with_null_reference_then_succeeds() {
        // given
        Member member = testMemberUtils.createSave();

        // when
        testBackgroundJobUtils.createSave(member);
        testBackgroundJobUtils.createSave(member);

        // then
        assertThat(backgroundJobRepository.findAll())
                .as("대상 애그리거트가 없는 job은 NULL 참조로 여러 건 존재할 수 있어야 함")
                .hasSize(2);
    }
}
