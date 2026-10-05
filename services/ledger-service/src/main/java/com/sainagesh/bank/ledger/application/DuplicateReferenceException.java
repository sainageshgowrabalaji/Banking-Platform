package com.sainagesh.bank.ledger.application;

/** Thrown when a reference was already used for a different entry or hold. */
public class DuplicateReferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateReferenceException(String message) {
        super(message);
    }
}
