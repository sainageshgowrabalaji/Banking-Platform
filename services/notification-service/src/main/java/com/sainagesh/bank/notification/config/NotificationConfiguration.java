package com.sainagesh.bank.notification.config;

import com.sainagesh.bank.events.Topics;
import com.sainagesh.bank.notification.adapter.in.kafka.UnreadableEventException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Clock;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

/** Spring wiring for the notification service. */
@Configuration
public class NotificationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfiguration.class);

    /** The suffix of the topic that failed events are parked on. */
    public static final String DEAD_LETTER_SUFFIX = ".dlt";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The topics this service reads, and the dead letter topic of each. Declaring them here means the
     * service starts cleanly even when it is the first one up and nobody has published yet.
     */
    @Bean
    KafkaAdmin.NewTopics notificationTopics(
            @Value("${bank.notification.topic-partitions:3}") int partitions,
            @Value("${bank.notification.topic-replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.PAYMENTS).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(Topics.ACCOUNTS).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(Topics.PAYMENTS + DEAD_LETTER_SUFFIX).partitions(1).replicas(replicas).build(),
                TopicBuilder.name(Topics.ACCOUNTS + DEAD_LETTER_SUFFIX).partitions(1).replicas(replicas).build());
    }

    /**
     * What happens when handling an event fails.
     *
     * <p>The event is tried again eight times, waiting 1, 2, 4, 8, 16 and then 30 seconds, about two
     * minutes in all. Most failures pass in that time (a database that was busy, a restart). If it
     * still fails, it is published to a dead letter topic, named after the original topic with ".dlt"
     * on the end, and the consumer moves on. One bad event must never block every event behind it. A
     * person can look at the dead letter topic and replay what is there.
     *
     * <p>An event that cannot even be read is not retried at all. It would fail the same way each time.
     *
     * <p>When the database itself is down, nothing is wrong with the event, so it is never parked.
     * The consumer tries the same event every five seconds until the database is back. Parking it
     * would mean a customer is never told, for a fault that had nothing to do with their payment.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka, (record, exception) -> {
            log.warn("Parking an event from {} on its dead letter topic. {}", record.topic(), exception.toString());
            // A negative partition lets Kafka pick one, so the dead letter topic may have any number of partitions.
            return new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1);
        });
        ExponentialBackOff waits = new ExponentialBackOff(1000L, 2.0);
        waits.setMaxInterval(30_000L);
        waits.setMaxAttempts(8);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, waits);
        handler.addNotRetryableExceptions(UnreadableEventException.class);
        handler.setBackOffFunction((record, exception) ->
                databaseIsDown(exception) ? new FixedBackOff(5000L, FixedBackOff.UNLIMITED_ATTEMPTS) : null);
        return handler;
    }

    /** True when the failure, or anything that caused it, says the database could not be reached or was too busy. */
    static boolean databaseIsDown(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransientDataAccessException
                    || cause instanceof DataAccessResourceFailureException
                    || cause instanceof CannotCreateTransactionException
                    || cause instanceof SQLTransientException
                    || cause instanceof SQLRecoverableException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
