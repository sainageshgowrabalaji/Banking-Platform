package com.sainagesh.bank.payments.domain;

/** Thrown when a payment is asked to make a move its current status does not allow. */
public class PaymentStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentStateException(String message) {
        super(message);
    }
}
