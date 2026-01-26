package site.one_question.question.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.global.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum QuestionExceptionSpec implements ExceptionSpec {
    QUESTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "QUESTION-001",
        "질문을 찾을 수 없음",
        "error.question.not-found"
    ),
    DAILY_QUESTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "QUESTION-002",
        "일별 질문을 찾을 수 없음",
        "error.question.daily-not-found"
    ),
    ANSWER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "QUESTION-003",
        "답변을 찾을 수 없음",
        "error.question.answer-not-found"
    ),
    ANSWER_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "QUESTION-004",
        "답변이 이미 존재함",
        "error.question.answer-already-exists"
    ),
    ALREADY_ANSWERED(
        HttpStatus.BAD_REQUEST,
        "QUESTION-005",
        "이미 답변한 질문",
        "error.question.already-answered"
    ),
    BEFORE_SIGNUP_DATE(
        HttpStatus.BAD_REQUEST,
        "QUESTION-006",
        "가입일 이전 날짜 요청",
        "error.question.before-signup-date"
    ),
    FUTURE_DATE_QUESTION(
        HttpStatus.BAD_REQUEST,
        "QUESTION-007",
        "미래 날짜 질문 요청",
        "error.question.future-date"
    ),
    RELOAD_LIMIT_EXCEEDED(
        HttpStatus.BAD_REQUEST,
        "QUESTION-008",
        "질문 변경 횟수 초과",
        "error.question.reload-limit-exceeded"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
