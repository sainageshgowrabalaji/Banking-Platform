package com.sainagesh.bank.payments.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Keeps a copy of every message exchanged with a rail. Banks must be able to show these for years. */
public interface PaymentMessages {

    /** @param direction OUT for a message this bank sent, IN for one it received */
    record Message(String direction, String type, String body, Instant at) {}

    void record(UUID paymentId, String direction, String type, String body);

    List<Message> of(UUID paymentId);
}
