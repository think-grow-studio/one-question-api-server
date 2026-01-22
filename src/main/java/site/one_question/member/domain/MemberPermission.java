package site.one_question.member.domain;

public enum MemberPermission {
    FREE(2),
    PREMIUM(4);

    private final int maxQuestionChangeCount;

    MemberPermission(int maxQuestionChangeCount) {
        this.maxQuestionChangeCount = maxQuestionChangeCount;
    }

    public int getMaxQuestionChangeCount() {
        return maxQuestionChangeCount;
    }
}
