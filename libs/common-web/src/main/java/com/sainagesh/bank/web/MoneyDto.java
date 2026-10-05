package com.sainagesh.bank.web;

import com.sainagesh.bank.money.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Money as it travels in JSON, {@code {"amount": "125.50", "currency": "USD"}}.
 *
 * <p>The amount is a string, never a JSON number. A JSON number becomes a double in many clients, and a
 * double cannot hold every cent exactly.
 */
public record MoneyDto(
        @NotNull @Pattern(regexp = "^-?[0-9]{1,15}([.][0-9]{1,6})?$", message = "must be a decimal number") String amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three letter currency code") String currency) {

    public static MoneyDto from(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }

    /** Turns the JSON form into exact money. A bad amount or currency is a client error, not a crash. */
    public Money toMoney() {
        try {
            return Money.of(amount, currency);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("INVALID_MONEY", "Not a valid amount of money: " + amount + " " + currency);
        }
    }

    /**
     * For an amount that moves or reserves money, which must be more than zero. A balance can be zero
     * or below, so the plain {@link #toMoney()} allows it.
     */
    public Money toPositiveMoney() {
        Money money = toMoney();
        if (!money.isPositive()) {
            throw ApiException.badRequest("INVALID_MONEY", "The amount must be greater than zero");
        }
        return money;
    }
}
