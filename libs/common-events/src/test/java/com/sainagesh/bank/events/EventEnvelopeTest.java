package com.sainagesh.bank.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void everyEventGetsItsOwnIdAndTime() {
        EventEnvelope<String> first = EventEnvelope.of("bank.accounts.opened", "ledger-service", "acc-1", "x");
        EventEnvelope<String> second = EventEnvelope.of("bank.accounts.opened", "ledger-service", "acc-1", "x");
        assertNotNull(first.time());
        assertNotEquals(first.id(), second.id());
        assertEquals("acc-1", first.subject());
    }

    @Test
    void anEventMustSayWhatItIsAbout() {
        assertThrows(NullPointerException.class, () -> EventEnvelope.of("t", "s", null, "x"));
    }
}
