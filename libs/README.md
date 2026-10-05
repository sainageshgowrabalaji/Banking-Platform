# Shared libraries

Small, stable code that every service uses. A library here must stay free of business rules, because a
change to it rebuilds every service.

| Library | What it holds |
|---|---|
| `common-money` | `Money`, an exact decimal amount with its currency |
| `common-events` | `EventEnvelope`, the wrapper around every Kafka event, and the topic names |
| `common-web` | `IdempotencyKey`, `MoneyDto`, `ApiException` and `Problems`, the shared error format, with one exception handler for every service |
| `common-messaging` | The transactional outbox (`Outbox`, `OutboxRelay`) and `ProcessedEvents`, which lets a consumer skip a duplicate |
| `common-idempotency` | `IdempotencyStore`, the table behind the `Idempotency-Key` header |
| `common-observability` | Traces, metrics and logs over OpenTelemetry, switched on by one environment variable |
| `common-testing` | Test support. A real PostgreSQL and Kafka for integration tests, the contract check, and the architecture rules. Test scope only |

`common-money` and `common-events` are plain Java with no dependencies.

The others plug themselves in. Each one has a Spring Boot auto-configuration, so a service gets the
outbox, the idempotency store or tracing by adding the library to its `pom.xml`. There is nothing to
wire by hand.

## What a service must bring

The libraries own code, not tables. A service that uses the outbox or the idempotency store creates
the tables in its own Flyway migration, because each service owns its own database.

| Library | Tables |
|---|---|
| `common-messaging` | `outbox_event` for a service that publishes, `processed_event` for one that consumes |
| `common-idempotency` | `idempotency_record` |

Copy them from `V1__ledger.sql` in the ledger service.

## Observability in one variable

`common-observability` follows the standard OpenTelemetry environment variables.

| Variable | Effect |
|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` | Send traces, metrics and logs to this collector |
| not set | Send nothing. Trace ids still appear in log lines, and `/actuator/prometheus` still works |
| `OTEL_TRACES_SAMPLER_ARG=0.1` | Keep one trace in ten. The default here keeps every one |

It leaves three things out of the traces because they are only noise. Health checks, scheduled jobs
that poll every second, and the spans Spring Security adds to every request.
