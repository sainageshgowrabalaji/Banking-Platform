package com.sainagesh.bank.payments.config;

import com.sainagesh.bank.events.Topics;
import com.sainagesh.bank.payments.application.SettlementAccounts;
import com.sainagesh.bank.payments.application.port.PaymentStore;
import com.sainagesh.bank.payments.domain.BusinessCalendar;
import com.sainagesh.bank.payments.domain.Rail;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring wiring for the payments service. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PaymentsProperties.class)
public class PaymentsConfiguration {

    /** One clock for the whole service, so a test can set the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BusinessCalendar businessCalendar(PaymentsProperties properties) {
        return new BusinessCalendar(properties.ach().cutOff());
    }

    /**
     * The circuit breakers of this service, with their numbers published as metrics.
     *
     * <p>A breaker watches the last 20 calls. When half of them failed it opens for ten seconds, and
     * every call in that time fails at once. It then lets three calls through as a test. If they work
     * it closes, and if not it opens again.
     */
    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    /**
     * How many payments are set aside for a person to look at. It should be zero. An alert on this
     * number is how the people who run the service find out.
     */
    @Bean
    MeterBinder parkedPayments(PaymentStore payments) {
        return meters -> Gauge.builder("bank.payments.parked", payments, PaymentStore::countParked)
                .description("Payments the saga gave up on after repeated errors. They need a person")
                .register(meters);
    }

    /** Where the money of a settled payment is credited. */
    @Bean
    SettlementAccounts settlementAccounts(PaymentsProperties properties) {
        return payment -> {
            if (payment.rail() == Rail.BOOK) {
                return UUID.fromString(payment.creditor().accountNumber());
            }
            return payment.rail() == Rail.ACH
                    ? properties.settlement().achAccount()
                    : properties.settlement().instantAccount();
        };
    }

    /** The topic this service writes to. Created at start if it is missing. */
    @Bean
    NewTopic paymentsTopic(PaymentsProperties properties) {
        return TopicBuilder.name(Topics.PAYMENTS)
                .partitions(properties.topicPartitions())
                .replicas(properties.topicReplicas())
                .build();
    }
}
