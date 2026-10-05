package com.sainagesh.bank.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequestHashTest {

    private final RequestHash hash = new RequestHash(JsonMapper.builder().build());

    record Pay(String to, String amount) {}

    @Test
    void theSameRequestAlwaysGivesTheSameHash() {
        assertEquals(hash.of(new Pay("acc-2", "10.00")), hash.of(new Pay("acc-2", "10.00")));
        assertEquals(64, hash.of(new Pay("acc-2", "10.00")).length());
    }

    @Test
    void aDifferentRequestGivesADifferentHash() {
        assertNotEquals(hash.of(new Pay("acc-2", "10.00")), hash.of(new Pay("acc-2", "10.01")));
    }
}
