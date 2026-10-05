package com.sainagesh.bank.ledger.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the ledger, under {@code bank.ledger}.
 *
 * @param holdLifetime        how long a hold lasts when the caller does not say
 * @param lockTimeout         how long a posting waits for an account another posting has locked
 * @param idempotencyRetention how long an Idempotency-Key is remembered
 * @param topicPartitions     partitions of the topics this service creates
 * @param topicReplicas       copies of each partition. One on a laptop, three in production
 */
@ConfigurationProperties("bank.ledger")
public record LedgerProperties(
        Duration holdLifetime,
        Duration lockTimeout,
        Duration idempotencyRetention,
        Integer topicPartitions,
        Integer topicReplicas) {

    public LedgerProperties {
        holdLifetime = holdLifetime == null ? Duration.ofDays(7) : holdLifetime;
        lockTimeout = lockTimeout == null ? Duration.ofSeconds(5) : lockTimeout;
        idempotencyRetention = idempotencyRetention == null ? Duration.ofHours(24) : idempotencyRetention;
        topicPartitions = topicPartitions == null ? 3 : topicPartitions;
        topicReplicas = topicReplicas == null ? 1 : topicReplicas;
    }
}
