package site.one_question.question.domain;

public enum HistoryDirection {
    PREVIOUS,  // baseDate 기준 이전 날짜들
    NEXT,      // baseDate 기준 이후 날짜들
    BOTH       // 양방향 (baseDate 포함)
}
