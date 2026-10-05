package com.sainagesh.bank.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Remembers which events a consumer has handled, in the {@code processed_event} table.
 *
 * <p>Kafka delivers at least once, so the same event can arrive twice. A consumer asks
 * {@link #firstTime} inside the transaction that does its work. The id and the work are saved together,
 * so an event is handled exactly once even when it is delivered many times.
 */
public class ProcessedEvents {

    private final JdbcClient jdbc;

    public ProcessedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records the event id. Returns true the first time, and false when this consumer has seen the id
     * before, in which case the caller does nothing.
     *
     * @param consumer the name of the consumer, so two consumers in one service do not block each other
     */
    public boolean firstTime(String consumer, UUID eventId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The event id must be recorded inside the transaction that does the work");
        }
        return jdbc.sql("""
                insert into processed_event (consumer, event_id, processed_at)
                values (:consumer, :eventId, now())
                on conflict (consumer, event_id) do nothing
                """)
                .param("consumer", consumer)
                .param("eventId", eventId)
                .update() == 1;
    }
}
