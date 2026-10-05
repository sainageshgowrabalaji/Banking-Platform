package com.sainagesh.bank.messaging;

import com.sainagesh.bank.events.EventEnvelope;

/**
 * Where a use case puts an event it wants published.
 *
 * <p>The event is stored in the service's own database, in the same transaction as the business change.
 * Either both are saved or neither is. A relay then sends it to Kafka. This is the transactional outbox
 * pattern, and it is the answer to the dual write problem.
 */
public interface Outbox {

    /**
     * Stores the event for publishing. Call it inside the transaction that makes the business change.
     *
     * @param topic the Kafka topic. The Kafka key is the event's subject
     */
    void publish(String topic, EventEnvelope<?> event);
}
