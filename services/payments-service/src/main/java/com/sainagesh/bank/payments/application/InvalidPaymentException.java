package com.sainagesh.bank.payments.application;

/** Thrown when a payment request cannot be right, whatever the state of the accounts. */
public class InvalidPaymentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPaymentException(String message) {
        super(message);
    }
}
