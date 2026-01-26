package site.one_question.global.exception.spec;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalExceptionSpec implements ExceptionSpec {
    VALIDATION_FAILED(
        HttpStatus.BAD_REQUEST,
        "GLOBAL-001",
        "요청 데이터 검증 실패",
        "error.global.validation"
    ),
    TYPE_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "GLOBAL-002",
        "타입 변환 실패",
        "error.global.type-mismatch"
    ),
    CONSTRAINT_VIOLATE(
        HttpStatus.BAD_REQUEST,
        "GLOBAL-003",
        "제약 조건 위반",
        "error.global.constraint-violate"
    ),
    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "GLOBAL-999",
        "서버 내부 오류",
        "error.global.internal"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
