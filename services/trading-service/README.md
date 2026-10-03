# trading-service

Takes orders, checks them and matches them.

| | |
|---|---|
| Area | Markets |
| Port | 8302 |
| Built in | Phase 2 |
| Patterns it will show | Single-writer, ring buffer, event sourcing, state machine |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- An order state machine that follows FIX order states
- A matching engine with price-time priority on the LMAX Disruptor
- Pre-trade risk checks (limits, fat-finger, buying power)
- A FIX 4.4 gateway built on QuickFIX/J

## Run it

```bash
scripts/run.sh trading-service
curl http://localhost:8302/v1/info
curl http://localhost:8302/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/trading/v1/info`.

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
