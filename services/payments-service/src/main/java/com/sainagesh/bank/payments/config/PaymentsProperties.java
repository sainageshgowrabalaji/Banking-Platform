package com.sainagesh.bank.payments.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the payments service, under {@code bank.payments}. Every value has a default that works
 * on a laptop, so the service starts with no settings at all.
 *
 * @param routingNumber        this bank's own routing number. The default is made up and belongs to no bank
 * @param idempotencyRetention how long an Idempotency-Key is remembered
 */
@ConfigurationProperties("bank.payments")
public record PaymentsProperties(
        LedgerClient ledger,
        Settlement settlement,
        Ach ach,
        InstantRail instant,
        ScreeningRules screening,
        String routingNumber,
        Duration idempotencyRetention,
        Integer topicPartitions,
        Integer topicReplicas) {

    public PaymentsProperties {
        ledger = ledger == null ? new LedgerClient(null, null, null) : ledger;
        settlement = settlement == null ? new Settlement(null, null) : settlement;
        ach = ach == null ? new Ach(null, null, null, null) : ach;
        instant = instant == null ? new InstantRail(null) : instant;
        screening = screening == null ? new ScreeningRules(null) : screening;
        routingNumber = routingNumber == null ? "999000017" : routingNumber;
        idempotencyRetention = idempotencyRetention == null ? Duration.ofHours(24) : idempotencyRetention;
        topicPartitions = topicPartitions == null ? 3 : topicPartitions;
        topicReplicas = topicReplicas == null ? 1 : topicReplicas;
    }

    /** How to reach the ledger service, and how long to wait for it. */
    public record LedgerClient(String url, Duration connectTimeout, Duration readTimeout) {

        public LedgerClient {
            url = url == null ? "http://localhost:8101" : url;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        }
    }

    /** The bank's own ledger accounts that money leaving on a rail is credited to. Created by the ledger's V2 migration. */
    public record Settlement(UUID achAccount, UUID instantAccount) {

        public Settlement {
            achAccount = achAccount == null ? UUID.fromString("00000000-0000-0000-0000-000000000101") : achAccount;
            instantAccount =
                    instantAccount == null ? UUID.fromString("00000000-0000-0000-0000-000000000102") : instantAccount;
        }
    }

    /**
     * @param settleAfter  how long the simulated network takes to settle a batch. Days in real life
     * @param cutOff       the Eastern time after which a payment goes out on the next business day
     * @param pollInterval how often the saga asks whether a waiting payment has settled
     */
    public record Ach(Duration batchInterval, Duration settleAfter, LocalTime cutOff, Duration pollInterval) {

        public Ach {
            batchInterval = batchInterval == null ? Duration.ofSeconds(15) : batchInterval;
            settleAfter = settleAfter == null ? Duration.ofSeconds(15) : settleAfter;
            cutOff = cutOff == null ? LocalTime.of(17, 0) : cutOff;
            pollInterval = pollInterval == null ? Duration.ofSeconds(5) : pollInterval;
        }
    }

    /** @param limit the largest single payment the instant rail carries */
    public record InstantRail(BigDecimal limit) {

        public InstantRail {
            limit = limit == null ? new BigDecimal("500000.00") : limit;
        }
    }

    /** @param blockedNames a payment to a receiver whose name contains one of these is stopped */
    public record ScreeningRules(List<String> blockedNames) {

        public ScreeningRules {
            blockedNames = blockedNames == null ? List.of("blocked person", "sanctioned entity") : List.copyOf(blockedNames);
        }
    }
}
