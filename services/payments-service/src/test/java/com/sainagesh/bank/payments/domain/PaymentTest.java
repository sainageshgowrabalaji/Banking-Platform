package com.sainagesh.bank.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The payment state machine. Every allowed move, and every move that must be refused. */
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");
    private static final UUID HOLD = UUID.randomUUID();

    private static Payment received() {
        return Payment.receive(
                UUID.randomUUID(),
                new Party("Jane Doe", "123456789", "021000021"),
                Money.of("125.50", "USD"),
                Rail.INSTANT,
                "INV-42",
                "Invoice 42",
                NOW);
    }

    private static Payment accepted() {
        Payment payment = received();
        payment.fundsHeld(HOLD);
        payment.accept();
        return payment;
    }

    private static Payment sent() {
        Payment payment = accepted();
        payment.startSending();
        payment.sent("NET-1", LocalDate.of(2026, 10, 5));
        return payment;
    }

    @Test
    void aPaymentStartsAsReceivedWithWorkToDo() {
        Payment payment = received();

        assertEquals(PaymentStatus.RCVD, payment.status());
        assertTrue(payment.needsWork());
        assertEquals("payment:" + payment.id(), payment.ledgerReference());
    }

    @Test
    void theHappyPathEndsSettledWithNothingLeftToDo() {
        Payment payment = sent();
        assertEquals(PaymentStatus.ACSP, payment.status());

        payment.settle(NOW.plusSeconds(3));

        assertEquals(PaymentStatus.ACSC, payment.status());
        assertEquals(NOW.plusSeconds(3), payment.settledAt());
        assertFalse(payment.holdReleasePending());
        assertFalse(payment.needsWork());
    }

    @Test
    void aPaymentCannotBeAcceptedBeforeItsMoneyIsReserved() {
        assertThrows(PaymentStateException.class, () -> received().accept());
    }

    @Test
    void stepsCannotBeSkipped() {
        assertThrows(PaymentStateException.class, () -> received().startSending());
        assertThrows(PaymentStateException.class, () -> accepted().sent("NET-1", LocalDate.now()));
        assertThrows(PaymentStateException.class, () -> received().settle(NOW));
        assertThrows(PaymentStateException.class, () -> accepted().settle(NOW));

        // Marked as leaving, but the rail has not taken it yet. It cannot be settled.
        Payment leaving = accepted();
        leaving.startSending();
        assertThrows(PaymentStateException.class, () -> leaving.settle(NOW));
    }

    @Test
    void aRejectionBeforeAnyMoneyWasReservedHasNothingToUndo() {
        Payment payment = received();

        payment.reject(ReasonCode.AM04, "Not enough money");

        assertEquals(PaymentStatus.RJCT, payment.status());
        assertEquals(ReasonCode.AM04, payment.reasonCode());
        assertFalse(payment.holdReleasePending());
        assertFalse(payment.needsWork());
    }

    @Test
    void aRejectionAfterTheMoneyWasReservedStillOwesTheRelease() {
        Payment payment = sent();

        payment.reject(ReasonCode.AC04, "The account is closed");

        assertEquals(PaymentStatus.RJCT, payment.status());
        assertTrue(payment.holdReleasePending());
        assertTrue(payment.needsWork());

        payment.holdReleased();

        assertFalse(payment.needsWork());
    }

    @Test
    void aPaymentCanBeCancelledUntilItIsSent() {
        Payment early = received();
        early.cancel();
        assertEquals(PaymentStatus.CANC, early.status());
        assertEquals(ReasonCode.CUST, early.reasonCode());
        // No hold id is known, but the ledger may have reserved the money in a call whose answer was
        // lost. So the undo step is still owed, and it will find out.
        assertTrue(early.holdReleasePending());

        Payment held = accepted();
        held.cancel();
        assertEquals(PaymentStatus.CANC, held.status());
        assertTrue(held.holdReleasePending());

        assertThrows(PaymentStateException.class, () -> sent().cancel());
    }

    @Test
    void theDoorToCancellingClosesBeforeTheRailIsCalled() {
        Payment leaving = accepted();
        leaving.startSending();

        // The rail may or may not have it by now. Nobody can know, so it can no longer be cancelled.
        assertEquals(PaymentStatus.ACSP, leaving.status());
        assertFalse(leaving.onRail());
        assertThrows(PaymentStateException.class, leaving::cancel);
    }

    @Test
    void aFinalStatusNeverChanges() {
        Payment settled = sent();
        settled.settle(NOW);
        assertThrows(PaymentStateException.class, () -> settled.reject(ReasonCode.MS03, null));
        assertThrows(PaymentStateException.class, settled::cancel);

        Payment rejected = received();
        rejected.reject(ReasonCode.AM04, null);
        assertThrows(PaymentStateException.class, () -> rejected.reject(ReasonCode.MS03, null));
        assertThrows(PaymentStateException.class, rejected::cancel);
        assertNull(rejected.settledAt());
    }

    @Test
    void aPaymentIsForMoreThanZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Payment.receive(
                        UUID.randomUUID(), new Party("Jane", "1", null), Money.zero("USD"), Rail.BOOK, null, null, NOW));
    }

    @Test
    void anUnknownReasonCodeBecomesNotSpecified() {
        assertEquals(ReasonCode.AC04, ReasonCode.fromCode("AC04"));
        assertEquals(ReasonCode.MS03, ReasonCode.fromCode("ZZ99"));
        assertEquals(ReasonCode.MS03, ReasonCode.fromCode(null));
    }
}
