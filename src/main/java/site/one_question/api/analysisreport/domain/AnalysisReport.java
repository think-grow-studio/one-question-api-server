package site.one_question.api.analysisreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportCompletionDataInvalidException;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportNotPendingException;
import site.one_question.api.member.domain.Member;
import site.one_question.common.domain.BaseEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "analysis_report")
public class AnalysisReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 100)
    private AnalysisReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisReportStatus status;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String result;

    @Column(length = 30)
    private String provider;

    @Column(length = 100)
    private String model;

    @Lob
    @Column(name = "llm_options", columnDefinition = "CLOB")
    private String llmOptions;

    public static AnalysisReport createPending(
            Member member,
            AnalysisReportType reportType
    ) {
        return new AnalysisReport(
                null,
                member,
                reportType,
                AnalysisReportStatus.PENDING,
                null,
                null,
                null,
                null
        );
    }

    public void complete(String result, String provider, String model, String llmOptions) {
        validatePending();
        validateCompletionData(result, provider, model, llmOptions);
        this.result = result;
        this.provider = provider;
        this.model = model;
        this.llmOptions = llmOptions;
        this.status = AnalysisReportStatus.COMPLETED;
    }

    public void fail() {
        validatePending();
        this.status = AnalysisReportStatus.FAILED;
    }

    private void validatePending() {
        if (this.status != AnalysisReportStatus.PENDING) {
            throw new AnalysisReportNotPendingException(id, status);
        }
    }

    private void validateCompletionData(String result, String provider, String model, String llmOptions) {
        if (isBlank(result) || isBlank(provider) || isBlank(model) || isBlank(llmOptions)) {
            throw new AnalysisReportCompletionDataInvalidException(id);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
