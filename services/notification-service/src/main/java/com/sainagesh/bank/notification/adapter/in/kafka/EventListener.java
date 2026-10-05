package com.sainagesh.bank.notification.adapter.in.kafka;

import com.sainagesh.bank.events.EventEnvelope;
import com.sainagesh.bank.events.Topics;
import com.sainagesh.bank.messaging.EventJson;
import com.sainagesh.bank.notification.application.NotifyCustomer;
import com.sainagesh.bank.notification.domain.EventFacts;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Listens to the payment and account topics and hands each event to the use case.
 *
 * <p>Events for one payment share a key, so they are on one partition and arrive here in the order
 * they happened. Different payments are handled side by side by the other consumers of the group.
 */
@Component
class EventListener {

    private final EventJson json;
    private final NotifyCustomer notifyCustomer;
    private final MeterRegistry meters;

    EventListener(EventJson json, NotifyCustomer notifyCustomer, MeterRegistry meters) {
        this.json = json;
        this.notifyCustomer = notifyCustomer;
        this.meters = meters;
    }

    @KafkaListener(topics = {Topics.PAYMENTS, Topics.ACCOUNTS}, groupId = "${spring.kafka.consumer.group-id:notification-service}")
    void onEvent(ConsumerRecord<String, String> record) {
        EventFacts facts = read(record.value());
        NotifyCustomer.Result result = notifyCustomer.handle(facts);
        meters.counter("bank.notifications", "type", facts.eventType(), "result", result.name()).increment();
    }

    /** Reads only the fields a message needs. Anything else in the event is ignored. */
    private EventFacts read(String value) {
        try {
            EventEnvelope<JsonNode> event = json.read(value);
            JsonNode data = event.data();
            String accountId = text(data, "debtorAccountId");
            if (accountId == null) {
                accountId = text(data, "accountId");
            }
            JsonNode amount = data.get("amount");
            return new EventFacts(
                    event.id(),
                    event.type(),
                    UUID.fromString(accountId),
                    amount == null ? null : text(amount, "amount"),
                    amount == null ? null : text(amount, "currency"),
                    text(data, "creditorName"),
                    text(data, "reasonCode"),
                    text(data, "type"));
        } catch (RuntimeException e) {
            throw new UnreadableEventException("The event could not be read", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
