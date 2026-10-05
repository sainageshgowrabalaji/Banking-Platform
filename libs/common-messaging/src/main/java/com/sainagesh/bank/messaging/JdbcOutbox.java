package com.sainagesh.bank.messaging;

import com.sainagesh.bank.events.EventEnvelope;
import java.sql.Timestamp;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Writes events to the {@code outbox_event} table. */
public class JdbcOutbox implements Outbox {

    private final JdbcClient jdbc;
    private final EventJson json;
    private final Supplier<String> traceparent;

    /**
     * @param traceparent gives the W3C trace context of the request being handled, or null when there is
     *                    none. It is stored with the event so the trace continues in the consumer
     */
    public JdbcOutbox(JdbcClient jdbc, EventJson json, Supplier<String> traceparent) {
        this.jdbc = jdbc;
        this.json = json;
        this.traceparent = traceparent;
    }

    /** A caller with no open transaction gets an error instead of a silent dual write. */
    @Override
    public void publish(String topic, EventEnvelope<?> event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "An event must be stored inside the transaction that makes the business change");
        }
        String trace = event.traceparent() != null ? event.traceparent() : traceparent.get();
        EventEnvelope<?> stamped = trace == null || trace.equals(event.traceparent())
                ? event
                : new EventEnvelope<>(
                        event.id(), event.type(), event.source(), event.subject(), event.time(), trace, event.data());
        jdbc.sql("""
                insert into outbox_event (id, topic, msg_key, type, payload, traceparent, created_at)
                values (:id, :topic, :key, :type, :payload, :traceparent, :createdAt)
                """)
                .param("id", stamped.id())
                .param("topic", topic)
                .param("key", stamped.subject())
                .param("type", stamped.type())
                .param("payload", json.write(stamped))
                .param("traceparent", trace)
                .param("createdAt", Timestamp.from(stamped.time()))
                .update();
    }
}
