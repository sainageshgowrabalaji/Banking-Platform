package com.sainagesh.bank.payments.application.port;

import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.PaymentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where payments are kept, together with the small amount of bookkeeping the saga needs. */
public interface PaymentStore {

    void insert(Payment payment, Instant firstAttemptAt);

    Optional<Payment> find(UUID id);

    /**
     * Saves the changes to a payment, says when the saga should look at it next, and releases the claim.
     *
     * @param nextAttemptAt null when there is nothing left to do
     * @throws org.springframework.dao.OptimisticLockingFailureException when someone else changed the
     *         payment since it was read
     */
    void update(Payment payment, Instant nextAttemptAt);

    /**
     * Takes a payment for one worker. Nobody else can take it until the claim is released or runs out.
     * This is what stops two copies of the service, or the worker and a cancel request, from working
     * on one payment at the same moment.
     *
     * @return the payment when the claim was won, or empty when someone else has it
     */
    Optional<Payment> claim(UUID id, Duration claimFor);

    void releaseClaim(UUID id);

    /** Nothing is left to do for this payment. Takes it off the worker's list and releases the claim. */
    void done(UUID id);

    /**
     * Records a failed attempt and schedules the next one, a little later each time (2, 4, 8 ... up to
     * 60 seconds). The claim is released.
     */
    void retryLater(UUID id, Instant now, String error);

    /**
     * Like {@link #retryLater}, for a failure that is not expected to pass by itself. It counts them.
     * At the given number in a row the payment is parked. It leaves the worker's list and stays as it
     * is until a person has looked at it.
     *
     * @return true when this failure parked the payment
     */
    boolean retryOrPark(UUID id, Instant now, String error, int parkAfter);

    /** How many payments are parked and waiting for a person. */
    long countParked();

    /** Nothing failed, there is just nothing to do yet. Look again at the given time. The claim is released. */
    void lookAgainAt(UUID id, Instant when);

    /** Ids of payments whose next attempt is due and that nobody has claimed. */
    List<UUID> findDue(Instant now, int limit);

    /** Asks the saga to look at a payment now. Used when a rail reports news about it. */
    void wake(UUID id);

    Page listByAccount(UUID debtorAccountId, PaymentStatus status, int limit, String cursor);

    /** @param nextCursor null on the last page */
    record Page(List<Payment> items, String nextCursor) {}
}
