package com.sainagesh.bank.messaging;

import com.sainagesh.bank.events.EventEnvelope;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns an event into the JSON that travels on Kafka, and back.
 *
 * <p>A consumer reads the payload as a JSON tree first. It then looks at the event type and decides which
 * class the payload should become, so one topic can carry several kinds of event.
 */
public final class EventJson {

    private static final TypeReference<EventEnvelope<JsonNode>> TREE = new TypeReference<>() {};

    private final JsonMapper mapper;

    public EventJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(EventEnvelope<?> event) {
        return mapper.writeValueAsString(event);
    }

    public EventEnvelope<JsonNode> read(String json) {
        return mapper.readValue(json, TREE);
    }

    /** Reads the payload of an event as the given class. */
    public <T> T data(EventEnvelope<JsonNode> event, Class<T> type) {
        return mapper.treeToValue(event.data(), type);
    }
}
