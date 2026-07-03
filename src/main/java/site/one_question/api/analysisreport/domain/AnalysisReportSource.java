package site.one_question.api.analysisreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import site.one_question.api.question.domain.DailyQuestion;
import site.one_question.api.question.domain.DailyQuestionAnswer;
import site.one_question.common.domain.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "analysis_report_source",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ars_report_answer",
                        columnNames = {"analysis_report_id", "daily_question_answer_id"}
                ),
                @UniqueConstraint(
                        name = "uk_ars_report_seq",
                        columnNames = {"analysis_report_id", "seq_no"}
                )
        }
)
public class AnalysisReportSource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_report_id", nullable = false)
    private AnalysisReport analysisReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_answer_id", nullable = false)
    private DailyQuestionAnswer dailyQuestionAnswer;

    @Column(name = "question_date", nullable = false)
    private LocalDate questionDate;

    @Column(name = "question_content", nullable = false, length = 500)
    private String questionContent;

    @Lob
    @Column(name = "answer_content", nullable = false, columnDefinition = "CLOB")
    private String answerContent;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    public static AnalysisReportSource create(
            AnalysisReport analysisReport,
            DailyQuestionAnswer dailyQuestionAnswer,
            int seqNo
    ) {
        String answerContent = dailyQuestionAnswer.getContent();
        if (answerContent.length() > DailyQuestionAnswer.MAX_CONTENT_LENGTH) {
            throw new IllegalStateException(
                    "답변 내용이 최대 길이를 초과함: dailyQuestionAnswerId=" + dailyQuestionAnswer.getId());
        }

        DailyQuestion dailyQuestion = dailyQuestionAnswer.getDailyQuestionId();
        return new AnalysisReportSource(
                null,
                analysisReport,
                dailyQuestionAnswer,
                dailyQuestion.getQuestionDate(),
                dailyQuestion.getQuestion().getContent(),
                answerContent,
                seqNo
        );
    }
}
