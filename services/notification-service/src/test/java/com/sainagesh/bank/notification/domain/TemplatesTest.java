package com.sainagesh.bank.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplatesTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();

    private static EventFacts payment(String type, String reasonCode) {
        return new EventFacts(UUID.randomUUID(), type, ACCOUNT, "125.50", "USD", "Jane Doe", reasonCode, null);
    }

    @Test
    void aSettledPaymentSaysWhoWasPaidAndHowMuch() {
        Notification message = Templates.messageFor(payment("bank.payments.settled", null), NOW).orElseThrow();

        assertEquals("Payment sent", message.title());
        assertEquals("Your payment of 125.50 USD to Jane Doe has been sent.", message.body());
        assertEquals(ACCOUNT, message.accountId());
        assertEquals(Channel.IN_APP, message.channel());
        assertEquals(NOW, message.createdAt());
    }

    @Test
    void aRejectedPaymentExplainsTheReasonInPlainWords() {
        Notification message = Templates.messageFor(payment("bank.payments.rejected", "AM04"), NOW).orElseThrow();

        assertEquals("Payment not sent", message.title());
        assertEquals(
                "Your payment of 125.50 USD to Jane Doe could not be sent because there was not enough money in your account."
                        + " No money has left your account.",
                message.body());
    }

    @Test
    void anUnknownReasonStillGivesAClearMessage() {
        Notification message = Templates.messageFor(payment("bank.payments.rejected", "ZZ99"), NOW).orElseThrow();

        assertTrue(message.body().contains("because the bank could not complete it"), message.body());
    }

    @Test
    void acceptedAndCancelledPaymentsHaveTheirOwnWords() {
        assertEquals("Payment on its way", Templates.messageFor(payment("bank.payments.accepted", null), NOW).orElseThrow().title());
        assertEquals("Payment cancelled", Templates.messageFor(payment("bank.payments.cancelled", "CUST"), NOW).orElseThrow().title());
    }

    @Test
    void aNewAccountIsWelcomed() {
        EventFacts facts = new EventFacts(UUID.randomUUID(), "bank.accounts.opened", ACCOUNT, null, null, null, null, "SAVINGS");

        Notification message = Templates.messageFor(facts, NOW).orElseThrow();

        assertEquals("Account opened", message.title());
        assertEquals("Your new savings account is open and ready to use.", message.body());
    }

    @Test
    void eventsThatAreNotForCustomersGiveNoMessage() {
        assertTrue(Templates.messageFor(payment("bank.ledger.entry-posted", null), NOW).isEmpty());
    }
}
