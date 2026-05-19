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
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
