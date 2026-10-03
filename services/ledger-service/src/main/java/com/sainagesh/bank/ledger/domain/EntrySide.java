package com.sainagesh.bank.ledger.domain;

/**
 * The two sides of double-entry bookkeeping.
 *
 * <p>A customer's deposit account is a liability of the bank, because the bank owes that money to the
 * customer. A credit makes a liability larger and a debit makes it smaller. So money leaving a customer
 * account is a debit, and money arriving is a credit.
 */
public enum EntrySide {
    DEBIT,
    CREDIT
}
