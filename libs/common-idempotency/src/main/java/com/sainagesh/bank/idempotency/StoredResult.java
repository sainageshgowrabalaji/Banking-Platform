package com.sainagesh.bank.idempotency;

/**
 * What the first request with a key produced.
 *
 * @param resourceId the id of the thing it created, such as an account id or a payment id
 * @param status     the HTTP status it returned
 */
public record StoredResult(String resourceId, int status) {}
