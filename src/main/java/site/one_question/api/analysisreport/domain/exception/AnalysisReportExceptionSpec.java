package site.one_question.api.analysisreport.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum AnalysisReportExceptionSpec implements ExceptionSpec {
    SOURCE_ANSWER_COUNT_INVALID(
            HttpStatus.BAD_REQUEST,
            "AI-REPORT-001",
            "분석 리포트 소스 답변 개수 오류",
            "error.ai-report.source-answer-count-invalid"
    ),
    SOURCE_ANSWER_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "AI-REPORT-002",
            "분석 리포트 소스 답변 중복",
            "error.ai-report.source-answer-duplicated"
    ),
    SOURCE_ANSWER_NOT_OWNED(
            HttpStatus.FORBIDDEN,
            "AI-REPORT-003",
            "분석 리포트 소스 답변 소유권 오류",
            "error.ai-report.source-answer-not-owned"
    ),
    REPORT_NOT_FOUND_FOR_JOB(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "AI-REPORT-004",
            "백그라운드 작업에 대응하는 분석 리포트 없음",
            "error.ai-report.not-found-for-job"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
