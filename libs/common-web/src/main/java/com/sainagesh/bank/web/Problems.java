package com.sainagesh.bank.web;

import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds error bodies in the RFC 9457 "problem details" format, so every service reports errors the same
 * way. Each problem carries a stable code a client can act on, and the trace id of the request when
 * tracing is on, so a customer's report can be matched to the exact request in the traces and logs.
 */
public final class Problems {

    private static final String TYPE_BASE = "https://errors.bank.example/";

    private Problems() {}

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        // The tracing library keeps the id of the current trace in the logging context.
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
