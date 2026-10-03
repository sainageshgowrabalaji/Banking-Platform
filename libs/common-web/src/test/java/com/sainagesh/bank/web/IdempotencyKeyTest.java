package com.sainagesh.bank.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void acceptsAUuid() {
        assertEquals("2f1c9d8e-7a44-4b0e-9a55-0d6f1c2b3a4e",
                new IdempotencyKey("2f1c9d8e-7a44-4b0e-9a55-0d6f1c2b3a4e").value());
    }

    @Test
    void rejectsMissingShortOrUnsafeKeys() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey(null));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("short"));
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey("has spaces in it"));
    }
}
