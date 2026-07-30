package site.one_question.api.analysisreport.domain;

import java.util.List;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerCountInvalidException;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerDuplicatedException;

/**
 * 리포트 소재가 될 답변 ID 집합.
 *
 * <p>요청의 ID 순서는 의미가 없다 — {@code seq_no} 는 {@code question_date} 기준으로 매기고
 * 조회는 IN 절이다. 그래서 생성 시점에 오름차순으로 정규화해 보관하며, 접근자 이름이
 * 그 사실을 드러낸다. 정규화가 없으면 같은 멱등키로 순서만 다른 목록을 재시도할 때
 * {@code request_hash} 가 갈려 409 로 거절된다.
 */
public record AnalysisReportSourceAnswerIds(List<Long> ascendingValues) {

    private static final int MIN_COUNT = 10;
    private static final int MAX_COUNT = 15;

    public AnalysisReportSourceAnswerIds {
        if (ascendingValues == null) {
            throw new AnalysisReportSourceAnswerCountInvalidException(0);
        }

        List<Long> distinctValues = ascendingValues.stream().distinct().toList();
        if (distinctValues.size() != ascendingValues.size()) {
            throw new AnalysisReportSourceAnswerDuplicatedException();
        }

        int count = distinctValues.size();
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new AnalysisReportSourceAnswerCountInvalidException(count);
        }

        ascendingValues = ascendingValues.stream().sorted().toList();
    }

    public int size() {
        return ascendingValues.size();
    }
}
