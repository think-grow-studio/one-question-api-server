package site.one_question.domain.analysisreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportStatus;
import site.one_question.api.analysisreport.domain.AnalysisReportType;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportCompletionDataInvalidException;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportNotPendingException;

@DisplayName("AnalysisReport 상태 전이")
class AnalysisReportTest {

    private AnalysisReport createPendingReport() {
        return AnalysisReport.createPending(null, AnalysisReportType.THINKING_PATTERN);
    }

    @Test
    @DisplayName("생성 직후 상태는 PENDING이고 결과 필드는 모두 NULL이다")
    void create_pending_then_status_is_pending_and_result_fields_are_null() {
        AnalysisReport report = createPendingReport();

        assertThat(report.getStatus()).isEqualTo(AnalysisReportStatus.PENDING);
        assertThat(report.getResult()).isNull();
        assertThat(report.getProvider()).isNull();
        assertThat(report.getModel()).isNull();
        assertThat(report.getLlmOptions()).isNull();
    }

    @Test
    @DisplayName("complete 시 결과 필드가 채워지고 COMPLETED로 전이된다")
    void complete_with_valid_data_then_fields_filled_and_status_completed() {
        AnalysisReport report = createPendingReport();

        report.complete("{\"summary\":\"...\"}", "anthropic", "claude-sonnet-5", "{\"temperature\":0.7}");

        assertThat(report.getStatus()).isEqualTo(AnalysisReportStatus.COMPLETED);
        assertThat(report.getResult()).isEqualTo("{\"summary\":\"...\"}");
        assertThat(report.getProvider()).isEqualTo("anthropic");
        assertThat(report.getModel()).isEqualTo("claude-sonnet-5");
        assertThat(report.getLlmOptions()).isEqualTo("{\"temperature\":0.7}");
    }

    @Test
    @DisplayName("fail 시 결과 필드는 NULL로 남고 FAILED로 전이된다")
    void fail_then_status_failed_and_result_fields_remain_null() {
        AnalysisReport report = createPendingReport();

        report.fail();

        assertThat(report.getStatus()).isEqualTo(AnalysisReportStatus.FAILED);
        assertThat(report.getResult()).isNull();
    }

    @Test
    @DisplayName("PENDING이 아닌 리포트는 complete할 수 없다")
    void complete_when_not_pending_then_throws() {
        AnalysisReport report = createPendingReport();
        report.complete("{}", "anthropic", "claude-sonnet-5", "{}");

        assertThatExceptionOfType(AnalysisReportNotPendingException.class)
                .isThrownBy(() -> report.complete("{}", "anthropic", "claude-sonnet-5", "{}"));
    }

    @Test
    @DisplayName("PENDING이 아닌 리포트는 fail할 수 없다")
    void fail_when_not_pending_then_throws() {
        AnalysisReport report = createPendingReport();
        report.fail();

        assertThatExceptionOfType(AnalysisReportNotPendingException.class)
                .isThrownBy(report::fail);
    }

    @Test
    @DisplayName("결과 데이터가 비어 있으면 complete할 수 없다")
    void complete_with_blank_data_then_throws() {
        AnalysisReport report = createPendingReport();

        assertThatExceptionOfType(AnalysisReportCompletionDataInvalidException.class)
                .isThrownBy(() -> report.complete(null, "anthropic", "claude-sonnet-5", "{}"));
        assertThatExceptionOfType(AnalysisReportCompletionDataInvalidException.class)
                .isThrownBy(() -> report.complete("{}", " ", "claude-sonnet-5", "{}"));
    }
}
