package com.sainagesh.bank.ledger.domain;

/**
 * The kinds of account the ledger keeps.
 *
 * <p>Each kind has a normal side, which is the side that makes its balance grow.
 *
 * <ul>
 *   <li>A customer account (checking, savings) is money the bank owes to the customer. That is a
 *       liability, and liabilities grow with a credit. So a deposit is a credit
 *   <li>An internal account holds the bank's own side of a movement, such as cash received or money on
 *       its way through a payment network. Those are assets, and assets grow with a debit
 * </ul>
 */
public enum AccountType {
    CHECKING(EntrySide.CREDIT, true),
    SAVINGS(EntrySide.CREDIT, true),
    INTERNAL(EntrySide.DEBIT, false);

    private final EntrySide normalSide;
    private final boolean customerOwned;

    AccountType(EntrySide normalSide, boolean customerOwned) {
        this.normalSide = normalSide;
        this.customerOwned = customerOwned;
    }

    /** The side that makes the balance of this kind of account grow. */
    public EntrySide normalSide() {
        return normalSide;
    }

    /** True for accounts that belong to a customer. Those can never be overdrawn. */
    public boolean customerOwned() {
        return customerOwned;
    }
}
