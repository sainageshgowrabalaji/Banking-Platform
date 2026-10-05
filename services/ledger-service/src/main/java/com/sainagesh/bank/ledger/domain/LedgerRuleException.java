package com.sainagesh.bank.ledger.domain;

/**
 * Thrown when a request would break a rule of the ledger. Each rule has a stable code that a caller
 * can act on.
 */
public class LedgerRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The rules. The names are part of the API, so they are never renamed. */
    public enum Rule {
        INSUFFICIENT_FUNDS,
        ACCOUNT_NOT_ACTIVE,
        ACCOUNT_FROZEN,
        ACCOUNT_CLOSED,
        ACCOUNT_NOT_EMPTY,
        CURRENCY_MISMATCH,
        HOLD_NOT_ACTIVE,
        HOLD_MISMATCH,
        INVALID_STATUS_CHANGE
    }

    private final Rule rule;

    public LedgerRuleException(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
