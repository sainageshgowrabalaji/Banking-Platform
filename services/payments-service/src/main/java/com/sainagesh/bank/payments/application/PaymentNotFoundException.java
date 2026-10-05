package com.sainagesh.bank.payments.application;

import java.util.UUID;

/** Thrown when there is no payment with the given id. */
public class PaymentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(UUID id) {
        super("There is no payment " + id);
    }
}
