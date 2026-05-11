package site.one_question.exception.spec;

import org.springframework.http.HttpStatus;

public interface ExceptionSpec {
    HttpStatus getStatus();
    String getCode();
    String getLogMessage();
    String getClientMessageKey();
    String name();
}
