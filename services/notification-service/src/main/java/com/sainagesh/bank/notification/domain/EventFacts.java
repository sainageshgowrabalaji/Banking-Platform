package com.sainagesh.bank.notification.domain;

import java.util.UUID;

/**
 * The few facts of an event that a message needs. The consumer fills this from the event payload,
 * and leaves a field null when the event does not carry it.
 *
 * <p>This service reads only what it needs and ignores every other field. So the services that publish
 * events can add fields without breaking it. That habit is called a tolerant reader.
 */
public record EventFacts(
        UUID eventId,
        String eventType,
        UUID accountId,
        String amount,
        String currency,
        String counterpartyName,
        String reasonCode,
        String accountType) {}
