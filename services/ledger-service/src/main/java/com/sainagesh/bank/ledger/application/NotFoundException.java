package com.sainagesh.bank.ledger.application;

/** Thrown when an account, hold or entry does not exist. */
public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static NotFoundException account(Object id) {
        return new NotFoundException("ACCOUNT_NOT_FOUND", "There is no account " + id);
    }

    public static NotFoundException hold(Object id) {
        return new NotFoundException("HOLD_NOT_FOUND", "There is no hold " + id);
    }

    public String code() {
        return code;
    }
}
