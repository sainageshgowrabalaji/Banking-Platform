package com.sainagesh.bank.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One message for a customer.
 *
 * @param eventId   the event that caused it. One event gives at most one message
 * @param accountId the account the message is about
 */
public record Notification(
        UUID id, UUID eventId, String eventType, UUID accountId, Channel channel, String title, String body, Instant createdAt) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
