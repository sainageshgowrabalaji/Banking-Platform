package com.sainagesh.bank.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsExactlyWhereADoubleWouldDrift() {
        Money total = Money.of("0.10", "USD").plus(Money.of("0.20", "USD"));
        assertEquals(Money.of("0.30", "USD"), total);
    }

    @Test
    void keepsTheScaleOfItsCurrency() {
        assertEquals("5.00 USD", Money.of("5", "USD").toString());
        assertEquals("500 JPY", Money.of("500", "JPY").toString());
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyHas() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1.005", "USD"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("1.5", "JPY"));
    }

    @Test
    void neverMixesCurrencies() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1.00", "USD").plus(Money.of("1.00", "EUR")));
    }

    @Test
    void convertsToAndFromMinorUnits() {
        assertEquals(12550, Money.of("125.50", "USD").toMinorUnits());
        assertEquals(Money.of("125.50", "USD"), Money.ofMinorUnits(12550, "USD"));
    }

    @Test
    void knowsItsSign() {
        assertTrue(Money.of("1.00", "USD").isPositive());
        assertTrue(Money.of("1.00", "USD").negate().isNegative());
        assertTrue(Money.zero("USD").isZero());
        assertFalse(Money.zero("USD").isPositive());
    }
}
