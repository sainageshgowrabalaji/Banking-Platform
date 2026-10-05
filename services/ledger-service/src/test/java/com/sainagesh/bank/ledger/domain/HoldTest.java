package com.sainagesh.bank.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sainagesh.bank.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldTest {

    private static final Instant NOW = Instant.parse("2026-10-05T14:00:00Z");

    private static Hold hold() {
        return Hold.place(UUID.randomUUID(), Money.of("25.00", "USD"), "payment:1", NOW, Duration.ofDays(7));
    }

    @Test
    void aNewHoldIsActiveAndExpiresLater() {
        Hold hold = hold();

        assertTrue(hold.isActive());
        assertEquals(NOW.plus(Duration.ofDays(7)), hold.expiresAt());
    }

    @Test
    void aHoldEndsOnceEitherCapturedOrReleased() {
        Hold captured = hold();
        captured.capture(NOW.plusSeconds(60));
        assertEquals(HoldStatus.CAPTURED, captured.status());
        assertEquals(NOW.plusSeconds(60), captured.closedAt());
        assertThrows(LedgerRuleException.class, () -> captured.release(NOW));

        Hold released = hold();
        released.release(NOW);
        assertEquals(HoldStatus.RELEASED, released.status());
        assertThrows(LedgerRuleException.class, () -> released.capture(NOW));
    }

    @Test
    void aHoldMustBeForMoreThanZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Hold.place(UUID.randomUUID(), Money.zero("USD"), "payment:2", NOW, Duration.ofDays(1)));
    }
}
