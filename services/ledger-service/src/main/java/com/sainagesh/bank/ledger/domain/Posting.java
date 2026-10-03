package com.sainagesh.bank.ledger.domain;

import com.sainagesh.bank.money.Money;
import java.util.Objects;

/**
 * One line of a journal entry. It moves an amount on one side of one account.
 *
 * <p>The amount is always greater than zero. The direction comes from the side, never from a minus sign.
 */
public record Posting(String accountId, EntrySide side, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("A posting amount must be greater than zero");
        }
    }

    public static Posting debit(String accountId, Money amount) {
        return new Posting(accountId, EntrySide.DEBIT, amount);
    }

    public static Posting credit(String accountId, Money amount) {
        return new Posting(accountId, EntrySide.CREDIT, amount);
    }
}
