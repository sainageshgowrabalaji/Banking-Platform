package com.sainagesh.bank.ledger.config;

import com.sainagesh.bank.events.Topics;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring wiring for the ledger. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LedgerProperties.class)
public class LedgerConfiguration {

    /** One clock for the whole service, so a test can set the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The topics this service writes to. They are created at start if they are missing. Three
     * partitions let three consumers share the work, and events with the same key always go to the
     * same partition, which keeps them in order.
     */
    @Bean
    NewTopic accountsTopic(LedgerProperties properties) {
        return TopicBuilder.name(Topics.ACCOUNTS)
                .partitions(properties.topicPartitions())
                .replicas(properties.topicReplicas())
                .build();
    }

    @Bean
    NewTopic ledgerTopic(LedgerProperties properties) {
        return TopicBuilder.name(Topics.LEDGER)
                .partitions(properties.topicPartitions())
                .replicas(properties.topicReplicas())
                .build();
    }
}
