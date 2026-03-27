package site.one_question.api.answerpost.domain.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import site.one_question.global.exception.spec.ExceptionSpec;

@Getter
@AllArgsConstructor
public enum AnswerPostExceptionSpec implements ExceptionSpec {
    ANSWER_POST_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "ANSWER-POST-001",
        "공개 답변을 찾을 수 없음",
        "error.answer-post.not-found"
    ),
    ALREADY_PUBLISHED(
        HttpStatus.CONFLICT,
        "ANSWER-POST-002",
        "이미 공개된 답변",
        "error.answer-post.already-published"
    ),
    ANSWER_POST_NOT_OWNED(
        HttpStatus.FORBIDDEN,
        "ANSWER-POST-003",
        "본인의 답변이 아님",
        "error.answer-post.not-owned"
    ),
    ANSWER_NOT_FOUND_FOR_PUBLISH(
        HttpStatus.NOT_FOUND,
        "ANSWER-POST-004",
        "공개할 답변을 찾을 수 없음",
        "error.answer-post.answer-not-found"
    );

    private final HttpStatus status;
    private final String code;
    private final String logMessage;
    private final String clientMessageKey;
}
