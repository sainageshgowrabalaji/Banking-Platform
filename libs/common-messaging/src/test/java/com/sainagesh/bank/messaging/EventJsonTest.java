package com.sainagesh.bank.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sainagesh.bank.events.EventEnvelope;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EventJsonTest {

    private final EventJson json = new EventJson(JsonMapper.builder().build());

    public record Opened(String accountId, String currency) {}

    @Test
    void anEventSurvivesTheTripToKafkaAndBack() {
        EventEnvelope<Opened> sent =
                EventEnvelope.of("bank.accounts.opened", "ledger-service", "acc-1", new Opened("acc-1", "USD"));

        EventEnvelope<JsonNode> received = json.read(json.write(sent));

        assertEquals(sent.id(), received.id());
        assertEquals("bank.accounts.opened", received.type());
        assertEquals("acc-1", received.subject());
        assertEquals(sent.time(), received.time());
        assertEquals(new Opened("acc-1", "USD"), json.data(received, Opened.class));
    }

    @Test
    void timesAreWrittenAsIsoTextNotAsNumbers() {
        EventEnvelope<Map<String, String>> event = EventEnvelope.of("t", "s", "k", Map.of("a", "b"));

        JsonNode tree = JsonMapper.builder().build().readTree(json.write(event));

        assertEquals(event.time().toString(), tree.get("time").asString());
    }
}
