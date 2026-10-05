package com.sainagesh.bank.ledger.application;

/** Thrown when a change kept colliding with other changes and was given up. The caller may retry. */
public class ConcurrentUpdateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
