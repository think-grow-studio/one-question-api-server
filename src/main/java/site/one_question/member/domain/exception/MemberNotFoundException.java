package site.one_question.member.domain.exception;

import java.util.Map;

public class MemberNotFoundException extends MemberException {

    public MemberNotFoundException(Long memberId) {
        super(MemberExceptionSpec.MEMBER_NOT_FOUND, Map.of("memberId", memberId));
    }
}
