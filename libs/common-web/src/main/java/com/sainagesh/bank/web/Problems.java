package com.sainagesh.bank.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds error bodies in the RFC 9457 "problem details" format, so every service reports errors the same
 * way. Each problem carries a stable code a client can act on.
 */
public final class Problems {

    private static final String TYPE_BASE = "https://errors.bank.example/";

    private Problems() {}

    public static ProblemDetail of(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
