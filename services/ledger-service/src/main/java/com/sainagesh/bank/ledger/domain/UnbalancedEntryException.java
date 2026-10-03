package com.sainagesh.bank.ledger.domain;

/** Thrown when a journal entry would break the rule that debits equal credits. */
public class UnbalancedEntryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnbalancedEntryException(String message) {
        super(message);
    }
}
