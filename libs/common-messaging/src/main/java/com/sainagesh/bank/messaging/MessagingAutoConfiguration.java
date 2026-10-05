package com.sainagesh.bank.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the outbox into any service that has a database, and the relay into any service that also has
 * Kafka. A service only needs this library on its classpath and the two tables in its schema.
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
            "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration"
        })
@EnableConfigurationProperties(OutboxProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JsonMapper.class)
    EventJson eventJson(JsonMapper mapper) {
        return new EventJson(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    ProcessedEvents processedEvents(JdbcClient jdbc) {
        return new ProcessedEvents(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(Outbox.class)
    @ConditionalOnBean({JdbcClient.class, EventJson.class})
    JdbcOutbox outbox(JdbcClient jdbc, EventJson json, ObjectProvider<TraceparentSource> traceparent) {
        TraceparentSource source = traceparent.getIfAvailable();
        Supplier<String> supplier = source != null ? source : () -> null;
        return new JdbcOutbox(jdbc, json, supplier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({JdbcClient.class, KafkaTemplate.class, PlatformTransactionManager.class, MeterRegistry.class})
    @ConditionalOnProperty(name = "bank.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager,
            KafkaTemplate<String, String> kafka,
            OutboxProperties properties,
            MeterRegistry meters) {
        return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), kafka, properties, meters);
    }

    /** Present only when the service has tracing on its classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer.class)
    static class TracingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(Tracer.class)
        TraceparentSource traceparentSource(Tracer tracer) {
            return () -> Traceparent.current(tracer);
        }
    }
}
