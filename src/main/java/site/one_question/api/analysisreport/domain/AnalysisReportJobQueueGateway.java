package site.one_question.api.analysisreport.domain;

import site.one_question.api.backgroundjob.domain.BackgroundJobMessage;

public interface AnalysisReportJobQueueGateway {

    /** 표준 트리거 메시지를 분석 리포트 큐로 보낸다. 직렬화(wire 변환)는 구현(어댑터)이 담당한다. */
    void send(BackgroundJobMessage message);
}
