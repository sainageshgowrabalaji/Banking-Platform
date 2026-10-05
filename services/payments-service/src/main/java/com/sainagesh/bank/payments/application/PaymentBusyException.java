package com.sainagesh.bank.payments.application;

import java.util.UUID;

/** Thrown when a payment is being worked on right now and cannot be changed by someone else. */
public class PaymentBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentBusyException(UUID id) {
        super("Payment " + id + " is being processed right now. Try again in a moment");
    }
}
