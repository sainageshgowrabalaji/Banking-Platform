package com.sainagesh.bank.web;

import java.util.regex.Pattern;

/**
 * The value of the {@code Idempotency-Key} header.
 *
 * <p>A client sends the same key when it retries a request. The server stores the first response under
 * that key and returns it again, so a customer who taps Send twice pays once.
 */
public record IdempotencyKey(String value) {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    public IdempotencyKey {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be 8 to 64 characters of letters, digits, dot, dash, underscore or colon");
        }
    }
}
