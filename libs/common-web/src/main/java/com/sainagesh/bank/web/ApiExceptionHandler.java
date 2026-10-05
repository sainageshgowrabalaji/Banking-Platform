package com.sainagesh.bank.web;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * One error handler for every service. Whatever goes wrong, the client gets the same RFC 9457 problem
 * details body with a stable {@code code}, and never a stack trace.
 *
 * <p>A service adds its own handler for its domain exceptions. That handler runs first because it is
 * more specific, and this one catches everything else.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> onApiException(ApiException e) {
        return respond(Problems.of(e.status(), e.code(), e.title(), e.getMessage()));
    }

    /** A request body that fails {@code @Valid}. Every bad field is listed, so a client can fix them all at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> onInvalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = Problems.of(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request is not valid", "One or more fields are not valid");
        problem.setProperty("fields", fields);
        return respond(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> onInvalidParameter(ConstraintViolationException e) {
        return respond(Problems.of(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request is not valid", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> onUnreadableBody(HttpMessageNotReadableException e) {
        return respond(Problems.of(
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request is not valid", "The request body could not be read"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> onMissingHeader(MissingRequestHeaderException e) {
        return respond(Problems.of(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                "The request is not valid",
                "The " + e.getHeaderName() + " header is required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> onBadParameter(MethodArgumentTypeMismatchException e) {
        return respond(Problems.of(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "The request is not valid",
                "The value of " + e.getName() + " is not valid"));
    }

    /** Everything else. Spring's own web errors keep their status. Anything unknown is a 500 and is logged. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> onAnythingElse(Exception e) {
        if (e instanceof ErrorResponse known) {
            HttpStatusCode status = known.getStatusCode();
            ProblemDetail body = known.getBody();
            ProblemDetail problem = Problems.of(
                    status,
                    codeFor(status),
                    body.getTitle() != null ? body.getTitle() : "Request failed",
                    body.getDetail());
            return respond(problem);
        }
        log.error("Unhandled error", e);
        return respond(Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Something went wrong on our side",
                "The request could not be completed. Try again later."));
    }

    private static String codeFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
