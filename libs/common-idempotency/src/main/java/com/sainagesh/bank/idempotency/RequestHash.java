package com.sainagesh.bank.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.json.JsonMapper;

/**
 * A fingerprint of a request body. It lets the store tell a true retry, which has the same body, from
 * a different request that reuses the key by mistake.
 */
public final class RequestHash {

    private final JsonMapper mapper;

    public RequestHash(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** The SHA-256 of the request written as JSON. Records write their fields in a fixed order, so the same request always gives the same hash. */
    public String of(Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK", e);
        }
    }
}
