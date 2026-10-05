package com.sainagesh.bank.ledger.adapter.in.web;

import com.sainagesh.bank.ledger.application.ConcurrentUpdateException;
import com.sainagesh.bank.ledger.application.DuplicateReferenceException;
import com.sainagesh.bank.ledger.application.InvalidCursorException;
import com.sainagesh.bank.ledger.application.NotFoundException;
import com.sainagesh.bank.ledger.domain.LedgerRuleException;
import com.sainagesh.bank.ledger.domain.UnbalancedEntryException;
import com.sainagesh.bank.web.Problems;
import org.springframework.core.Ordered;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the ledger's own exceptions into problem details. The domain throws plain exceptions and knows
 * nothing about HTTP. This class is the one place that decides which status each rule becomes.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class LedgerExceptionHandler {

    @ExceptionHandler(LedgerRuleException.class)
    ResponseEntity<ProblemDetail> onRule(LedgerRuleException e) {
        HttpStatus status =
                switch (e.rule()) {
                    // The request was fine but a rule about money or the account refused it.
                    case INSUFFICIENT_FUNDS,
                            ACCOUNT_NOT_ACTIVE,
                            ACCOUNT_FROZEN,
                            ACCOUNT_CLOSED,
                            CURRENCY_MISMATCH,
                            HOLD_MISMATCH ->
                        HttpStatus.UNPROCESSABLE_CONTENT;
                    // The thing is not in a state that allows this change.
                    case ACCOUNT_NOT_EMPTY, HOLD_NOT_ACTIVE, INVALID_STATUS_CHANGE -> HttpStatus.CONFLICT;
                };
        return respond(Problems.of(status, e.rule().name(), "A ledger rule refused the request", e.getMessage()));
    }

    @ExceptionHandler(UnbalancedEntryException.class)
    ResponseEntity<ProblemDetail> onUnbalanced(UnbalancedEntryException e) {
        return respond(Problems.of(
                HttpStatus.UNPROCESSABLE_CONTENT, "UNBALANCED_ENTRY", "Debits must equal credits", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> onNotFound(NotFoundException e) {
        return respond(Problems.of(HttpStatus.NOT_FOUND, e.code(), "Not found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateReferenceException.class)
    ResponseEntity<ProblemDetail> onDuplicate(DuplicateReferenceException e) {
        return respond(Problems.of(
                HttpStatus.CONFLICT, "DUPLICATE_REFERENCE", "The reference was already used", e.getMessage()));
    }

    /**
     * The account stayed locked by other work. The second type is what the database layer throws when
     * a use case that does not retry by itself, such as a freeze, could not get its lock in time.
     */
    @ExceptionHandler({ConcurrentUpdateException.class, ConcurrencyFailureException.class})
    ResponseEntity<ProblemDetail> onBusy(RuntimeException e) {
        ProblemDetail problem = Problems.of(
                HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The account is busy", "Try the request again in a moment");
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
