package com.sainagesh.bank.payments.application.port;

import com.sainagesh.bank.money.Money;
import com.sainagesh.bank.payments.domain.ReasonCode;
import java.util.UUID;

/**
 * What the payments service needs from the ledger. Every call is safe to repeat, because each one
 * carries the payment's reference and the ledger does a reference only once.
 *
 * <p>A refusal by a ledger rule comes back as a result. A ledger that is slow or down throws
 * {@link LedgerUnavailableException}, and the saga tries again later.
 */
public interface Ledger {

    HoldResult placeHold(UUID accountId, Money amount, String reference);

    void releaseHold(UUID holdId);

    /** Posts the entry that moves the money, and ends the hold in the same ledger transaction. */
    PostResult postTransfer(String reference, UUID debitAccountId, UUID creditAccountId, Money amount, UUID holdId);

    sealed interface HoldResult {
        record Placed(UUID holdId) implements HoldResult {}

        record Refused(ReasonCode reason, String detail) implements HoldResult {}
    }

    sealed interface PostResult {
        record Posted(UUID entryId) implements PostResult {}

        record Refused(ReasonCode reason, String detail) implements PostResult {}
    }

    /**
     * The ledger said no in a way this service has no rule for. Trying again will most likely get the
     * same answer, so after a few tries the payment is set aside for a person to look at.
     */
    class UnexpectedRefusalException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnexpectedRefusalException(String message) {
            super(message);
        }
    }

    /** The ledger could not be reached, or answered with a server error. Nothing is known, so retry. */
    class LedgerUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public LedgerUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
