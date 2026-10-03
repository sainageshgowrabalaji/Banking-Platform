package com.sainagesh.bank.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount of money in one currency.
 *
 * <p>Money is never a {@code double}. A double cannot hold 0.10 exactly, and a bank cannot be off by a
 * cent. The amount is a {@link BigDecimal} at the currency's own scale (2 places for USD, 0 for JPY),
 * and two amounts in different currencies can never be added by accident.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                    "Too many decimal places for " + currency.getCurrencyCode() + ": " + amount.toPlainString());
        }
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    /** Parses the API form, such as {@code Money.of("125.50", "USD")}. */
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    /** Builds money from the smallest unit, such as cents. This is how amounts are stored. */
    public static Money ofMinorUnits(long minorUnits, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return new Money(BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits()), currency);
    }

    public static Money zero(String currencyCode) {
        return ofMinorUnits(0, currencyCode);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /** The amount in the smallest unit, such as cents. */
    public long toMinorUnits() {
        return amount.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " and " + other.currency.getCurrencyCode());
        }
    }
}
