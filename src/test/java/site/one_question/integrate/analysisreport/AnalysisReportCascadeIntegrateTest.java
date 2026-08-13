package site.one_question.integrate.analysisreport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.one_question.api.analysisreport.domain.AnalysisReport;
import site.one_question.api.analysisreport.domain.AnalysisReportSource;
import site.one_question.api.member.domain.Member;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.api.question.domain.Question;
import site.one_question.api.question.domain.QuestionCycle;
import site.one_question.integrate.test_config.IntegrateTest;

@DisplayName("AI 분석 리포트 연관관계 통합 테스트")
class AnalysisReportCascadeIntegrateTest extends IntegrateTest {

    @Test
    @DisplayName("리포트를 삭제하면 source만 함께 삭제한다")
    void delete_report_then_deletes_sources_only() {
        Member member = testMemberUtils.createSave();
        QuestionCycle cycle = testQuestionCycleUtils.createSave(member);
        Question question = testQuestionUtils.createSave_With_Content("질문");
        DailyQuestion dailyQuestion = testDailyQuestionUtils.createSave_With_Date(
                member, cycle, question, LocalDate.of(2026, 8, 13));
        DailyQuestionAnswer answer = testDailyQuestionAnswerUtils.createSave_With_Content(
                dailyQuestion, member, "답변");
        AnalysisReport report = testAnalysisReportUtils.createSave(member);
        AnalysisReportSource source = testAnalysisReportSourceUtils.createSave(report, answer, 1);

        transactionTemplate.executeWithoutResult(status -> {
            analysisReportRepository.deleteById(report.getId());
            analysisReportRepository.flush();
        });

        assertThat(analysisReportSourceRepository.existsById(source.getId()))
                .as("리포트 source는 cascade로 삭제되어야 한다")
                .isFalse();
        assertThat(dailyQuestionAnswerRepository.existsById(answer.getId()))
                .as("원본 답변은 삭제되지 않아야 한다")
                .isTrue();
    }
}
