package com.sainagesh.bank.payments.application.port;

import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.payments.domain.ReasonCode;
import java.time.Duration;
import java.time.LocalDate;

/**
 * One payment rail. There is one implementation per rail, and the saga picks the right one by asking
 * each which rail it serves. This is the strategy pattern. Adding a rail means adding a class, and the
 * saga does not change.
 */
public interface RailGateway {

    Rail rail();

    /** Hands the payment to the rail. Safe to repeat for the same payment. */
    Submission submit(Payment payment);

    /**
     * True when a settled payment has really left this bank. Then the receiver has the money whatever
     * happens next here, and the payment can no longer be answered with a rejection. A book transfer
     * never leaves, so it is the one rail that says false.
     */
    default boolean leavesTheBank() {
        return true;
    }

    /** Asks the rail what became of a payment it was given. */
    Outcome outcome(Payment payment);

    sealed interface Submission {
        /** @param settlementDate the day the rail says the money will arrive */
        record Sent(String railReference, LocalDate settlementDate) implements Submission {}

        record Refused(ReasonCode reason, String detail) implements Submission {}
    }

    sealed interface Outcome {
        record Settled() implements Outcome {}

        record Returned(ReasonCode reason, String detail) implements Outcome {}

        /** The rail has no answer yet. */
        record Pending(Duration askAgainIn) implements Outcome {}
    }
}
