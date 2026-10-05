package com.sainagesh.bank.payments.iso20022;

/** Thrown when a message cannot be read, or lacks a field this platform needs. */
public class MessageFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MessageFormatException(String message) {
        super(message);
    }

    public MessageFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
