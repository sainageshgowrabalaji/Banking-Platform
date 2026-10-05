package com.sainagesh.bank.notification.adapter.in.kafka;

/**
 * Thrown for a message that can never be read, such as broken JSON. Trying it again would fail the
 * same way, so it goes straight to the dead letter topic.
 */
public class UnreadableEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnreadableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
