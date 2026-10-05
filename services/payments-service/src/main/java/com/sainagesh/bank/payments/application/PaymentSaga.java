package com.sainagesh.bank.payments.application;

import com.sainagesh.bank.payments.application.port.Ledger;
import com.sainagesh.bank.payments.application.port.Ledger.HoldResult;
import com.sainagesh.bank.payments.application.port.Ledger.LedgerUnavailableException;
import com.sainagesh.bank.payments.application.port.Ledger.PostResult;
import com.sainagesh.bank.payments.application.port.PaymentEvents;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.application.port.RailGateway;
import com.sainagesh.bank.payments.application.port.RailGateway.Outcome;
import com.sainagesh.bank.payments.application.port.RailGateway.Submission;
import com.sainagesh.bank.payments.application.port.Screening;
import com.sainagesh.bank.payments.domain.Payment;
import com.sainagesh.bank.payments.domain.Rail;
import com.sainagesh.bank.payments.domain.ReasonCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The payment saga. It moves one payment from received to a final status, one step at a time.
 *
 * <p>A payment touches three systems (this service, the ledger and a rail) and no single database
 * transaction can cover them. So the work is a chain of small steps. Each step calls one other system,
 * then saves the result here. If a later step fails, an undo step repairs what an earlier one did.
 *
 * <pre>
 *   step                          undo
 *   1. reserve the money (hold)   release the hold
 *   2. screen the payment         nothing to undo
 *   3. send it on its rail        nothing, a rail refusal means it never left
 *   4. post the entry             nothing, this is the point of no return
 * </pre>
 *
 * <p>Step 3 is saved in two parts. First "sending", then the call to the rail, then "sent". The
 * customer may cancel only before "sending" is saved. Without that order a payment could be cancelled
 * in the short moment after the rail took it and before this service wrote that down.
 *
 * <p>Four things make this safe when anything can fail at any moment.
 *
 * <ul>
 *   <li><b>The state is in the database.</b> If the service stops, the worker finds the payment again
 *       and carries on from the status it was left in
 *   <li><b>Every outside call can be repeated.</b> Each one carries the payment's own reference, and the
 *       ledger and the rails do a reference only once. Doing a step twice is harmless
 *   <li><b>One worker at a time.</b> A step first claims the payment, so two copies of the service never
 *       work on the same one
 *   <li><b>Two kinds of failure.</b> An outage (the ledger or the database is down) is waited out for
 *       as long as it takes. Any other error is tried a few times and then the payment is parked for
 *       a person, so one broken payment cannot spin forever
 * </ul>
 *
 * <p>This is an orchestrated saga. One class knows the whole route. The other style, choreography, has
 * each service react to the others' events with no one in charge. Orchestration is easier to follow
 * and to debug, which matters more than anything else when the subject is money.
 */
@Service
public class PaymentSaga {

    private static final Logger log = LoggerFactory.getLogger(PaymentSaga.class);
    private static final Duration CLAIM = Duration.ofSeconds(60);
    private static final int MAX_STEPS_PER_RUN = 8;

    private final PaymentStore payments;
    private final Ledger ledger;
    private final Screening screening;
    private final PaymentEvents events;
    private final SettlementAccounts settlementAccounts;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Map<Rail, RailGateway> rails = new EnumMap<>(Rail.class);
    private final int parkAfter;

    public PaymentSaga(
            PaymentStore payments,
            Ledger ledger,
            Screening screening,
            PaymentEvents events,
            SettlementAccounts settlementAccounts,
            List<RailGateway> railGateways,
            TransactionTemplate transaction,
            Clock clock,
            @Value("${bank.payments.park-after:10}") int parkAfter) {
        this.parkAfter = parkAfter;
        this.payments = payments;
        this.ledger = ledger;
        this.screening = screening;
        this.events = events;
        this.settlementAccounts = settlementAccounts;
        this.transaction = transaction;
        this.clock = clock;
        railGateways.forEach(gateway -> rails.put(gateway.rail(), gateway));
    }

    /** What one step decided. */
    private enum Next {
        /** The payment changed and may be able to take another step right away. */
        CONTINUE,
        /** Nothing more can be done for now. */
        STOP
    }

    /**
     * Moves a payment forward as far as it can go right now. Safe to call at any time and from anywhere.
     * If someone else is working on the payment, this does nothing.
     */
    public void advance(UUID paymentId) {
        for (int i = 0; i < MAX_STEPS_PER_RUN; i++) {
            Optional<Payment> claimed = payments.claim(paymentId, CLAIM);
            if (claimed.isEmpty()) {
                return;
            }
            Payment payment = claimed.get();
            try {
                Next next = step(payment);
                if (next == Next.STOP) {
                    return;
                }
            } catch (LedgerUnavailableException | DataAccessException | TransactionException e) {
                // Something is down or busy. Nothing is known about the outcome, so leave the payment
                // as it is and try the same step again later, for as long as it takes. Waiting longer
                // each time gives a system that is down room to recover.
                log.warn("Payment {} step from {} failed. It will be tried again. {}", paymentId, payment.status(), e.toString());
                payments.retryLater(paymentId, clock.instant(), abbreviate(e.toString()));
                return;
            } catch (RuntimeException e) {
                // Not an outage. Something about this one payment does not work, and repeating the
                // step forever would not change that. Try a few more times, then set it aside.
                boolean parked = payments.retryOrPark(paymentId, clock.instant(), abbreviate(e.toString()), parkAfter);
                if (parked) {
                    log.error(
                            "Payment {} is parked at {} after {} failed tries and needs a person to look at it. Last error. {}",
                            paymentId,
                            payment.status(),
                            parkAfter,
                            e.toString());
                } else {
                    log.warn("Payment {} step from {} failed. It will be tried again. {}", paymentId, payment.status(), e.toString());
                }
                return;
            }
        }
    }

    private Next step(Payment payment) {
        if (payment.status().isFinal()) {
            return payment.holdReleasePending() ? releaseHold(payment) : finished(payment);
        }
        return switch (payment.status()) {
            case RCVD -> reserveAndScreen(payment);
            case ACCP -> startSending(payment);
            case ACSP -> payment.onRail() ? settle(payment) : sendOnRail(payment);
            default -> finished(payment);
        };
    }

    /** Step 1 and 2. Reserve the money, then run the checks. */
    private Next reserveAndScreen(Payment payment) {
        if (payment.holdId() == null) {
            HoldResult hold = ledger.placeHold(payment.debtorAccountId(), payment.amount(), payment.ledgerReference());
            if (hold instanceof HoldResult.Refused refused) {
                save(payment, p -> p.reject(refused.reason(), refused.detail()), events::rejected);
                return Next.CONTINUE;
            }
            UUID holdId = ((HoldResult.Placed) hold).holdId();
            // Save the hold id before anything else, so the undo step can always find the hold.
            save(payment, p -> p.fundsHeld(holdId), null);
            return Next.CONTINUE;
        }
        Screening.Result result = screening.screen(payment);
        if (!result.clear()) {
            save(payment, p -> p.reject(ReasonCode.RR04, result.detail()), events::rejected);
        } else {
            save(payment, Payment::accept, events::accepted);
        }
        return Next.CONTINUE;
    }

    /** Step 3, first part. Write down that the payment is about to leave, which closes the door on cancelling. */
    private Next startSending(Payment payment) {
        save(payment, Payment::startSending, null);
        return Next.CONTINUE;
    }

    /**
     * Step 3, second part. Hand the payment to its rail. If the service stops between the call and the
     * save, this runs again, which is safe because a rail takes one payment only once.
     */
    private Next sendOnRail(Payment payment) {
        Submission submission = rail(payment).submit(payment);
        if (submission instanceof Submission.Refused refused) {
            save(payment, p -> p.reject(refused.reason(), refused.detail()), events::rejected);
        } else {
            Submission.Sent sent = (Submission.Sent) submission;
            save(payment, p -> p.sent(sent.railReference(), sent.settlementDate()), null);
        }
        return Next.CONTINUE;
    }

    /** Step 4. When the rail has settled, post the entry that really moves the money. */
    private Next settle(Payment payment) {
        Outcome outcome = rail(payment).outcome(payment);
        if (outcome instanceof Outcome.Pending pending) {
            payments.lookAgainAt(payment.id(), clock.instant().plus(pending.askAgainIn()));
            return Next.STOP;
        }
        if (outcome instanceof Outcome.Returned returned) {
            save(payment, p -> p.reject(returned.reason(), returned.detail()), events::rejected);
            return Next.CONTINUE;
        }
        PostResult posted = ledger.postTransfer(
                payment.ledgerReference(),
                payment.debtorAccountId(),
                settlementAccounts.creditAccountFor(payment),
                payment.amount(),
                payment.holdId());
        if (posted instanceof PostResult.Refused refused) {
            if (rail(payment).leavesTheBank()) {
                // The rail has settled, so the receiver has the money. Calling the payment rejected
                // now would tell the customer nothing left their account when it did. The entry has
                // to be posted. That needs whatever blocks it to be fixed first, for example a freeze
                // put on the account after the payment was sent.
                throw new IllegalStateException("Payment " + payment.id() + " has settled on the " + payment.rail()
                        + " rail but the ledger will not post it. " + refused.reason() + " " + refused.detail());
            }
            save(payment, p -> p.reject(refused.reason(), refused.detail()), events::rejected);
            return Next.CONTINUE;
        }
        Instant now = clock.instant();
        save(payment, p -> p.settle(now), events::settled);
        return Next.CONTINUE;
    }

    /** The undo step. Give the reserved money back after a rejection or a cancellation. */
    private Next releaseHold(Payment payment) {
        UUID holdId = payment.holdId();
        if (holdId == null) {
            // Cancelled before any hold id was saved. The ledger may still have reserved the money, in
            // a call whose answer never arrived. Asking for the same hold again settles it. The ledger
            // gives back the hold it has, or makes it now, and either way its id is known. If the
            // ledger refuses, nothing is reserved and nothing has to be given back.
            HoldResult hold = ledger.placeHold(payment.debtorAccountId(), payment.amount(), payment.ledgerReference());
            if (hold instanceof HoldResult.Placed placed) {
                holdId = placed.holdId();
            }
        }
        if (holdId != null) {
            ledger.releaseHold(holdId);
        }
        save(payment, Payment::holdReleased, null);
        return Next.CONTINUE;
    }

    private Next finished(Payment payment) {
        payments.done(payment.id());
        return Next.STOP;
    }

    /**
     * Applies one change and saves it, with its event, in one transaction. If the payment still needs
     * work it is scheduled for another look right away. If not, it is taken off the worker's list.
     */
    private void save(Payment payment, Consumer<Payment> change, Consumer<Payment> announce) {
        transaction.executeWithoutResult(status -> {
            change.accept(payment);
            payments.update(payment, payment.needsWork() ? clock.instant() : null);
            if (announce != null) {
                announce.accept(payment);
            }
        });
    }

    private RailGateway rail(Payment payment) {
        RailGateway gateway = rails.get(payment.rail());
        if (gateway == null) {
            throw new IllegalStateException("No gateway for rail " + payment.rail());
        }
        return gateway;
    }

    private static String abbreviate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
