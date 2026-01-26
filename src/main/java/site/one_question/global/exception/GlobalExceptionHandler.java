package site.one_question.global.exception;

import static site.one_question.global.exception.spec.GlobalExceptionSpec.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";
    private final MessageResolver messageResolver;

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ExceptionResponse> handleBaseException(BaseException e) {
        log.error(e.getLogMessage());

        String clientMessage = messageResolver.resolve(e.getClientMessageKey(), e.getMessageArgs());

        ExceptionResponse response = ExceptionResponse.of(
                getTraceId(),
                e.getSpec().getStatus().value(),
                e.getSpec().getCode(),
                clientMessage);
        return ResponseEntity.status(e.getSpec().getStatus()).body(response);
    }

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String errorDetails = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.error("[{}] {}", VALIDATION_FAILED.getCode(), errorDetails);
        String message = messageResolver.resolve(VALIDATION_FAILED.getClientMessageKey());
        ExceptionResponse response = ExceptionResponse.of(
                getTraceId(),
                VALIDATION_FAILED.getStatus().value(),
                VALIDATION_FAILED.getCode(),
                message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConstraintViolationException(
            ConstraintViolationException e) {

        logConstraintViolation(e);
        String message = messageResolver.resolve(CONSTRAINT_VIOLATE.getClientMessageKey());
        ExceptionResponse response = ExceptionResponse.of(
                getTraceId(),
                CONSTRAINT_VIOLATE.getStatus().value(),
                CONSTRAINT_VIOLATE.getCode(),
                message);
        return ResponseEntity.badRequest().body(response);
    }

    private void logConstraintViolation(ConstraintViolationException e) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            joiner.add(String.format("%s : %s = %s",
                v.getMessage(), v.getPropertyPath(), v.getInvalidValue()));
        }
        log.error("[{}] {}", CONSTRAINT_VIOLATE.getCode(), joiner);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {

        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        log.error("[{}] {} = {} -> {} Type", TYPE_MISMATCH.getCode(), ex.getName(), ex.getValue(), type);

        String message = messageResolver.resolve(TYPE_MISMATCH.getClientMessageKey());
        ExceptionResponse response = ExceptionResponse.of(
                getTraceId(),
                TYPE_MISMATCH.getStatus().value(),
                TYPE_MISMATCH.getCode(),
                message);
        return ResponseEntity.badRequest().body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Throwable cause = ex.getCause();
        String code = VALIDATION_FAILED.getCode();
        if (cause instanceof InvalidFormatException ife) {
            log.error("[{}] Invalid format: '{}' -> '{}'", code, getFieldNameFromPath(ife.getPath()), ife.getValue());
        } else if (cause instanceof UnrecognizedPropertyException upe) {
            log.error("[{}] Unrecognized property: '{}'", code, upe.getPropertyName());
        } else if (cause instanceof JsonParseException jpe) {
            log.error("[{}] JSON parse error: {}", code, jpe.getOriginalMessage());
        } else if (cause instanceof MismatchedInputException mie) {
            log.error("[{}] Mismatched input: '{}'", code, getFieldNameFromPath(mie.getPath()));
        } else {
            log.error("[{}] {}", code, ex.getMessage());
        }

        String message = messageResolver.resolve(VALIDATION_FAILED.getClientMessageKey());
        ExceptionResponse response = ExceptionResponse.of(
                getTraceId(),
                VALIDATION_FAILED.getStatus().value(),
                VALIDATION_FAILED.getCode(),
                message);
        return ResponseEntity.badRequest().body(response);
    }

    private String getFieldNameFromPath(List<Reference> path) {
        if (path == null || path.isEmpty()) return "unknown";
        StringBuilder sb = new StringBuilder();
        for (Reference ref : path) {
            if (!sb.isEmpty()) sb.append(".");
            if (ref.getFieldName() != null) {
                sb.append(ref.getFieldName());
            } else {
                sb.append("[").append(ref.getIndex()).append("]");
            }
        }
        return sb.toString();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleUnknown(Exception e) {
        log.error("[{}] {}", INTERNAL_ERROR.getCode(), e.getMessage(), e);

        String message = messageResolver.resolve(INTERNAL_ERROR.getClientMessageKey());
        ExceptionResponse response = ExceptionResponse.of(
                getTraceId(),
                INTERNAL_ERROR.getStatus().value(),
                INTERNAL_ERROR.getCode(),
                message);
        return ResponseEntity.internalServerError().body(response);
    }

    private String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : "N/A";
    }
}
