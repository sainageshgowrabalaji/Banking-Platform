package com.sainagesh.bank.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The wrapper around every event published to Kafka. The fields follow the CloudEvents model.
 *
 * @param id          unique id of this event. Consumers use it to skip duplicates
 * @param type        what happened, such as {@code bank.payments.settled}
 * @param source      the service that published it
 * @param subject     the business key, such as an account id. It is also the Kafka key, so all events for
 *                    one subject stay in order
 * @param time        when it happened
 * @param traceparent the W3C trace context, so one trace follows a payment across services
 * @param data        the event payload
 */
public record EventEnvelope<T>(
        UUID id, String type, String source, String subject, Instant time, String traceparent, T data) {

    public EventEnvelope {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(data, "data");
    }

    public static <T> EventEnvelope<T> of(String type, String source, String subject, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), type, source, subject, Instant.now(), null, data);
    }
}
