# 0011. OpenTelemetry for traces, metrics and logs

Status. Accepted, October 2026.

## Context

To follow a payment across four services, their traces, metrics and logs must share ids and land in
one place. The choices were a Java agent attached at start, or the support built into Spring Boot 4,
which uses Micrometer for the measuring and OpenTelemetry for the sending.

## Decision

Each service uses Spring Boot's OpenTelemetry starter, through `libs/common-observability`. The
standard variable `OTEL_EXPORTER_OTLP_ENDPOINT` switches the sending on. Trace context crosses HTTP
in the `traceparent` header. For Kafka it is saved in the outbox row with the event and sent as a
record header, so the consumer joins the trace of the request that caused the event. Log lines reach
OpenTelemetry through a small Logback appender of our own.

## What follows

One trace shows a payment from the gateway to the notification. No agent has to be attached, and
with no collector address nothing is sent. The appender is about 60 lines and uses only the stable
OpenTelemetry API, which avoids a library that is still marked alpha and has to match the
OpenTelemetry version exactly. The cost is that database calls are not traced, since that is what the
agent would have added.
