package com.sainagesh.bank.payments.adapter.in.web;

import com.sainagesh.bank.payments.application.InvalidCursorException;
import com.sainagesh.bank.payments.application.InvalidPaymentException;
import com.sainagesh.bank.payments.application.PaymentBusyException;
import com.sainagesh.bank.payments.application.PaymentNotFoundException;
import com.sainagesh.bank.payments.domain.PaymentStateException;
import com.sainagesh.bank.payments.iso20022.MessageFormatException;
import com.sainagesh.bank.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the payments service's own exceptions into problem details. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaymentsExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ProblemDetail> onNotFound(PaymentNotFoundException e) {
        return respond(Problems.of(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Not found", e.getMessage()));
    }

    @ExceptionHandler(InvalidPaymentException.class)
    ResponseEntity<ProblemDetail> onInvalid(InvalidPaymentException e) {
        return respond(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request is not valid", e.getMessage()));
    }

    @ExceptionHandler(MessageFormatException.class)
    ResponseEntity<ProblemDetail> onBadMessage(MessageFormatException e) {
        return respond(Problems.of(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "The message could not be read", e.getMessage()));
    }

    /** A move the payment's status does not allow, such as cancelling one that was already sent. */
    @ExceptionHandler(PaymentStateException.class)
    ResponseEntity<ProblemDetail> onWrongState(PaymentStateException e) {
        return respond(Problems.of(
                HttpStatus.CONFLICT, "PAYMENT_STATE_CONFLICT", "The payment does not allow this now", e.getMessage()));
    }

    @ExceptionHandler(PaymentBusyException.class)
    ResponseEntity<ProblemDetail> onBusy(PaymentBusyException e) {
        ProblemDetail problem =
                Problems.of(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS", "The payment is being processed", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ProblemDetail> onBadCursor(InvalidCursorException e) {
        return respond(Problems.of(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "The request is not valid", e.getMessage()));
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
