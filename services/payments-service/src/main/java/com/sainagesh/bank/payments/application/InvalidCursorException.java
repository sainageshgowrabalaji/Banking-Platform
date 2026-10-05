package com.sainagesh.bank.payments.application;

/** Thrown when a paging cursor is not one this service handed out. */
public class InvalidCursorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCursorException() {
        super("The cursor is not valid");
    }
}
