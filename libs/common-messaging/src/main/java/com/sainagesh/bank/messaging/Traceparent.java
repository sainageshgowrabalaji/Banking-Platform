package com.sainagesh.bank.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

/** Builds the W3C {@code traceparent} value of the span that is active on this thread. */
final class Traceparent {

    private Traceparent() {}

    static String current(Tracer tracer) {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        boolean sampled = Boolean.TRUE.equals(context.sampled());
        return "00-" + context.traceId() + "-" + context.spanId() + (sampled ? "-01" : "-00");
    }
}
