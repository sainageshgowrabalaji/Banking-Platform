# payments-service

The life of a payment, from request to settlement.

| | |
|---|---|
| Area | Banking |
| Port | 8102 |
| Built in | Phase 1 |
| Patterns it will show | Saga, state machine, strategy per rail, idempotent receiver |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- A payment saga (hold funds, screen, send, then settle or release)
- ISO 20022 messages in and out (pain.001, pacs.008, pacs.002)
- Three rails (book transfers, an ACH batch simulator, instant payments)
- Cut-off times and business days
- An idempotency store, so a retry never pays twice

## Run it

```bash
scripts/run.sh payments-service
curl http://localhost:8102/v1/info
curl http://localhost:8102/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/payments/v1/info`.

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
