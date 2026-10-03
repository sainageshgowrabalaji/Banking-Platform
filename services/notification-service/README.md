# notification-service

Tells customers what happened, driven by events.

| | |
|---|---|
| Area | Shared |
| Port | 8103 |
| Built in | Phase 1 |
| Patterns it will show | Event-driven consumer, idempotent consumer, dead-letter queue |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- A Kafka consumer for payment and account events
- Duplicate events skipped by event id
- Message templates for email, SMS and in-app alerts
- Failed messages parked on a dead-letter topic

## Run it

```bash
scripts/run.sh notification-service
curl http://localhost:8103/v1/info
curl http://localhost:8103/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/notifications/v1/info`.

## Layout

Every service uses the same hexagonal layout, so the business rules never depend on a framework.

```
domain/                    business rules in plain Java, no Spring
application/               use cases and the ports (interfaces) they need
adapter/in/web/            REST controllers
adapter/out/persistence/   database code that implements the ports
adapter/out/messaging/     Kafka code, including the outbox
config/                    Spring wiring
```
