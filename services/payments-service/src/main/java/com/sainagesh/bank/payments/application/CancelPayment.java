package com.sainagesh.bank.payments.application;

import com.sainagesh.bank.payments.application.port.PaymentEvents;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.PaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Cancels a payment that has not been sent on its rail yet, and gives the reserved money back. */
@Service
public class CancelPayment {

    private static final Logger log = LoggerFactory.getLogger(CancelPayment.class);

    private final PaymentStore payments;
    private final PaymentEvents events;
    private final PaymentSaga saga;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CancelPayment(
            PaymentStore payments, PaymentEvents events, PaymentSaga saga, TransactionTemplate transaction, Clock clock) {
        this.payments = payments;
        this.events = events;
        this.saga = saga;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Cancelling takes the same claim the saga takes. So a cancel and a saga step can never run on one
     * payment at the same moment, and a payment cannot be cancelled in the middle of being sent.
     *
     * <p>Cancelling an already cancelled payment returns it unchanged, so a retry is safe.
     */
    public Payment cancel(UUID paymentId) {
        Payment current = payments.find(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (current.status() == PaymentStatus.CANC) {
            return current;
        }
        Payment payment = payments.claim(paymentId, Duration.ofSeconds(30)).orElseThrow(() -> new PaymentBusyException(paymentId));
        try {
            transaction.executeWithoutResult(status -> {
                payment.cancel();
                payments.update(payment, payment.needsWork() ? clock.instant() : null);
                events.cancelled(payment);
            });
        } catch (RuntimeException e) {
            payments.releaseClaim(paymentId);
            throw e;
        }
        try {
            // The undo step. Release the hold now if the ledger is up, or leave it to the worker.
            saga.advance(paymentId);
        } catch (RuntimeException e) {
            log.warn("Payment {} is cancelled. The worker will release its hold. {}", paymentId, e.toString());
        }
        return payments.find(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
