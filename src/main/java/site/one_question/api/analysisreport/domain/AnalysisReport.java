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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import site.one_question.api.backgroundjob.domain.BackgroundJob;
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "background_job_id", nullable = false, unique = true)
    private BackgroundJob backgroundJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 100)
    private AnalysisReportType reportType;

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
            BackgroundJob backgroundJob,
            Member member,
            AnalysisReportType reportType
    ) {
        return new AnalysisReport(
                null,
                backgroundJob,
                member,
                reportType,
                null,
                null,
                null,
                null
        );
    }
}
