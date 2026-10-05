package com.sainagesh.bank.testing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Lets a test read what a service really published to Kafka. It reads the topic from the start with
 * its own consumer group, so it sees every message and disturbs nobody.
 */
public final class KafkaProbe implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> seen = new ArrayList<>();

    public KafkaProbe(String... topics) {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, TestInfrastructure.kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()));
        consumer.subscribe(List.of(topics));
    }

    /**
     * Waits until a message matches, and returns it. Fails with every message it did see, which makes a
     * failing test easy to understand.
     */
    public ConsumerRecord<String, String> awaitRecord(Predicate<ConsumerRecord<String, String>> match, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : seen) {
                if (match.test(record)) {
                    return record;
                }
            }
            consumer.poll(Duration.ofMillis(250)).forEach(seen::add);
        }
        for (ConsumerRecord<String, String> record : seen) {
            if (match.test(record)) {
                return record;
            }
        }
        throw new AssertionError("No matching message within " + timeout + ". Saw " + seen.size() + " messages: "
                + seen.stream().map(ConsumerRecord::value).toList());
    }

    /** Every message read so far. */
    public List<ConsumerRecord<String, String>> seen() {
        consumer.poll(Duration.ofMillis(250)).forEach(seen::add);
        return List.copyOf(seen);
    }

    @Override
    public void close() {
        consumer.close();
    }
}
