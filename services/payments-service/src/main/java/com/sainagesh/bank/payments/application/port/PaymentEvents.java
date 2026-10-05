package com.sainagesh.bank.payments.application.port;

import com.sainagesh.bank.payments.domain.Payment;

/** The facts the payments service announces. Called inside the transaction that changes the payment. */
public interface PaymentEvents {

    void accepted(Payment payment);

    void settled(Payment payment);

    void rejected(Payment payment);

    void cancelled(Payment payment);
}
