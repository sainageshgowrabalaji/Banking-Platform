package com.sainagesh.bank.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends stored events to Kafka, oldest first, and marks each one as published once Kafka confirms it.
 *
 * <p>Delivery is at least once. If the service stops after Kafka confirmed an event but before the mark
 * was saved, the event is sent again on the next start. That is why every consumer skips event ids it
 * has already handled.
 *
 * <p>Only one relay works at a time, even when several copies of the service run. The relay takes a
 * PostgreSQL advisory lock for the length of its transaction, and a copy that does not get the lock
 * simply waits for its next turn.
 *
 * <p>Events leave in the order their rows were written. For events of one key to leave in the order
 * they happened, the code that writes them must itself take turns for that key, for example by
 * holding a lock on the row they describe. Both services here do. A payment is changed only by
 * whoever has claimed it, and a ledger event has a key of its own.
 */
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Any fixed number. Every relay of one database asks for the same lock. */
    private static final long LOCK_KEY = 7_203_198_411L;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Counter published;
    private final AtomicLong waiting = new AtomicLong();

    private volatile boolean running;
    private Thread thread;
    private Instant lastCleanup = Instant.EPOCH;

    public OutboxRelay(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            KafkaTemplate<String, String> kafka,
            OutboxProperties properties,
            MeterRegistry meters) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.kafka = kafka;
        this.properties = properties;
        this.published = Counter.builder("bank.outbox.published")
                .description("Events sent to Kafka from the outbox")
                .register(meters);
        Gauge.builder("bank.outbox.waiting", waiting, AtomicLong::doubleValue)
                .description("Events stored but not yet sent. A number that keeps growing means Kafka is unreachable")
                .register(meters);
    }

    private record Row(long seq, String id, String topic, String key, String type, String payload, String traceparent) {}

    /**
     * One pass. Returns how many events were sent. Public so a test can drive the relay by hand.
     */
    public int relayOnce() {
        Integer sent = transaction.execute(status -> {
            Boolean locked = jdbc.sql("select pg_try_advisory_xact_lock(:key)")
                    .param("key", LOCK_KEY)
                    .query(Boolean.class)
                    .single();
            if (!Boolean.TRUE.equals(locked)) {
                return 0;
            }
            List<Row> rows = jdbc.sql("""
                    select seq, id::text as id, topic, msg_key, type, payload, traceparent
                    from outbox_event
                    where published_at is null
                    order by seq
                    limit :limit
                    """)
                    .param("limit", properties.batchSize())
                    .query((rs, n) -> new Row(
                            rs.getLong("seq"),
                            rs.getString("id"),
                            rs.getString("topic"),
                            rs.getString("msg_key"),
                            rs.getString("type"),
                            rs.getString("payload"),
                            rs.getString("traceparent")))
                    .list();
            List<Long> done = new ArrayList<>();
            for (Row row : rows) {
                try {
                    kafka.send(toRecord(row)).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    done.add(row.seq());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Stop at the first failure, so nothing overtakes the event that did not go out.
                    log.warn("Could not send event {} to {}. It will be tried again. {}", row.id(), row.topic(), e.toString());
                    break;
                }
            }
            if (!done.isEmpty()) {
                jdbc.sql("update outbox_event set published_at = now() where seq in (:seqs)")
                        .param("seqs", done)
                        .update();
            }
            return done.size();
        });
        int count = sent == null ? 0 : sent;
        if (count > 0) {
            published.increment(count);
        }
        return count;
    }

    private static ProducerRecord<String, String> toRecord(Row row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
        record.headers().add("event-id", row.id().getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-type", row.type().getBytes(StandardCharsets.UTF_8));
        if (row.traceparent() != null) {
            // The standard W3C header. A consumer with tracing on continues the same trace from it.
            record.headers().add("traceparent", row.traceparent().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private void housekeeping() {
        Long count = jdbc.sql("select count(*) from outbox_event where published_at is null")
                .query(Long.class)
                .single();
        waiting.set(count == null ? 0 : count);
        if (lastCleanup.plusSeconds(3600).isBefore(Instant.now())) {
            lastCleanup = Instant.now();
            int removed = jdbc.sql("delete from outbox_event where published_at < now() - make_interval(secs => :seconds)")
                    .param("seconds", properties.retention().toSeconds())
                    .update();
            if (removed > 0) {
                log.info("Removed {} published events older than {}", removed, properties.retention());
            }
        }
    }

    private void loop() {
        Instant lastHousekeeping = Instant.EPOCH;
        while (running) {
            int sent = 0;
            try {
                sent = relayOnce();
                // By the clock, not by quiet passes. A service that is never quiet still gets cleaned.
                if (lastHousekeeping.plusSeconds(10).isBefore(Instant.now())) {
                    lastHousekeeping = Instant.now();
                    housekeeping();
                }
            } catch (Exception e) {
                log.warn("Outbox relay pass failed. It will run again. {}", e.toString());
            }
            if (sent < properties.batchSize()) {
                try {
                    Thread.sleep(properties.pollInterval().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = Thread.ofVirtual().name("outbox-relay").start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start late and stop early, while the database and Kafka connections are still open. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
