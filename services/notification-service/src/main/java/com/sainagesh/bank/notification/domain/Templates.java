package com.sainagesh.bank.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The words of each message. One template per kind of event. */
public final class Templates {

    /** ISO 20022 reason codes in words a customer understands. */
    private static final Map<String, String> REASONS = Map.of(
            "AC01", "the account number was not correct",
            "AC04", "the receiving account is closed",
            "AC06", "the receiving account is blocked",
            "AM02", "the amount is more than this kind of payment allows",
            "AM04", "there was not enough money in your account",
            "RR04", "it did not pass a required check");

    private Templates() {}

    /** The message for an event, or empty when the event is not one a customer is told about. */
    public static Optional<Notification> messageFor(EventFacts facts, Instant now) {
        String money = facts.amount() + " " + facts.currency();
        String to = facts.counterpartyName() == null ? "the receiver" : facts.counterpartyName();
        return switch (facts.eventType()) {
            case "bank.payments.accepted" -> message(facts, now, "Payment on its way",
                    "Your payment of " + money + " to " + to + " is being processed.");
            case "bank.payments.settled" -> message(facts, now, "Payment sent",
                    "Your payment of " + money + " to " + to + " has been sent.");
            case "bank.payments.rejected" -> message(facts, now, "Payment not sent",
                    "Your payment of " + money + " to " + to + " could not be sent because " + reason(facts.reasonCode())
                            + ". No money has left your account.");
            case "bank.payments.cancelled" -> message(facts, now, "Payment cancelled",
                    "Your payment of " + money + " to " + to + " was cancelled. No money has left your account.");
            case "bank.accounts.opened" -> message(facts, now, "Account opened",
                    "Your new " + (facts.accountType() == null ? "" : facts.accountType().toLowerCase() + " ")
                            + "account is open and ready to use.");
            default -> Optional.empty();
        };
    }

    private static String reason(String code) {
        return REASONS.getOrDefault(code == null ? "" : code, "the bank could not complete it");
    }

    private static Optional<Notification> message(EventFacts facts, Instant now, String title, String body) {
        return Optional.of(new Notification(
                UUID.randomUUID(), facts.eventId(), facts.eventType(), facts.accountId(), Channel.IN_APP, title, body, now));
    }
}
