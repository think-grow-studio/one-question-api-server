package site.one_question.api.publicquestion.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum PublicQuestionExceptionSpec implements ExceptionSpec {
    EMPTY_ANSWER_CONTENT(
        HttpStatus.BAD_REQUEST,
        "PUBLIC-QUESTION-001",
        "답변 내용이 비어있음",
        "error.public-question.empty-answer-content"
    ),
    ANSWER_CONTENT_TOO_LONG(
        HttpStatus.BAD_REQUEST,
        "PUBLIC-QUESTION-002",
        "답변 내용 길이 초과",
        "error.public-question.answer-content-too-long"
    ),
    PDQ_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "PUBLIC-QUESTION-003",
        "공개 일일 질문을 찾을 수 없음",
        "error.public-question.pdq-not-found"
    ),
    PUBLIC_ANSWER_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "PUBLIC-QUESTION-004",
        "이미 답변한 공개 일일 질문",
        "error.public-question.answer-already-exists"
    ),
    PUBLIC_ANSWER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "PUBLIC-QUESTION-005",
        "공개 일일 질문 답변을 찾을 수 없음",
        "error.public-question.answer-not-found"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
