package com.sainagesh.bank.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the outbox relay, under {@code bank.outbox}.
 *
 * @param relayEnabled  switch the relay off in a test that only wants to look at the table
 * @param pollInterval  how long the relay waits when the table is empty
 * @param batchSize     how many events it sends in one pass
 * @param sendTimeout   how long it waits for Kafka to confirm one event
 * @param retention     how long a published event stays in the table before it is removed
 */
@ConfigurationProperties("bank.outbox")
public record OutboxProperties(
        Boolean relayEnabled, Duration pollInterval, Integer batchSize, Duration sendTimeout, Duration retention) {

    public OutboxProperties {
        relayEnabled = relayEnabled == null ? Boolean.TRUE : relayEnabled;
        pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
        batchSize = batchSize == null ? 100 : batchSize;
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(10) : sendTimeout;
        retention = retention == null ? Duration.ofDays(7) : retention;
    }
}
