package site.one_question.api.analysisreport.domain;

import java.util.List;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerCountInvalidException;
import site.one_question.api.analysisreport.domain.exception.AnalysisReportSourceAnswerDuplicatedException;

public record AnalysisReportSourceAnswerIds(List<Long> values) {

    private static final int MIN_COUNT = 10;
    private static final int MAX_COUNT = 15;

    public AnalysisReportSourceAnswerIds {
        if (values == null) {
            throw new AnalysisReportSourceAnswerCountInvalidException(0);
        }

        List<Long> distinctValues = values.stream().distinct().toList();
        if (distinctValues.size() != values.size()) {
            throw new AnalysisReportSourceAnswerDuplicatedException();
        }

        int count = distinctValues.size();
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new AnalysisReportSourceAnswerCountInvalidException(count);
        }

        // 요청의 ID 순서는 의미가 없다 — seq_no 는 question_date 기준으로 매기고,
        // 조회는 IN 절이다. 오름차순으로 정규화해 같은 답변 집합이면 요청 순서와
        // 무관하게 같은 값이 되도록 한다. 이 보장이 없으면 같은 멱등키로 순서만
        // 다른 목록을 재시도할 때 request_hash 가 갈려 409 로 거절된다.
        values = values.stream().sorted().toList();
    }
}
