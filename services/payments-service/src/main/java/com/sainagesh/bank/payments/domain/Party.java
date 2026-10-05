package com.sainagesh.bank.payments.domain;

import java.util.Objects;

/**
 * The person or company on the receiving side of a payment.
 *
 * @param name          the name on the account
 * @param accountNumber the account to credit. For a BOOK payment this is an account id in this bank
 * @param routingNumber the nine digit number of the receiving bank. Needed for ACH and INSTANT
 */
public record Party(String name, String accountNumber, String routingNumber) {

    public Party {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(accountNumber, "accountNumber");
        if (name.isBlank() || accountNumber.isBlank()) {
            throw new IllegalArgumentException("A party needs a name and an account number");
        }
    }
}
