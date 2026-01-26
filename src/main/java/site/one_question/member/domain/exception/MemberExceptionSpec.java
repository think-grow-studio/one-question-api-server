package site.one_question.member.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.global.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum MemberExceptionSpec implements ExceptionSpec {
    MEMBER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "MEMBER-001",
        "회원을 찾을 수 없음",
        "error.member.not-found"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
