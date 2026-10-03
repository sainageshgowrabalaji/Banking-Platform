# positions-service

What each account holds and what it is worth.

| | |
|---|---|
| Area | Markets |
| Port | 8303 |
| Built in | Phase 3 |
| Patterns it will show | CQRS read model, event replay, batch processing |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- Positions built from fills
- Realised and unrealised profit and loss
- Value at Risk from historical simulation
- Option Greeks from Black-Scholes
- An end-of-day batch

## Run it

```bash
scripts/run.sh positions-service
curl http://localhost:8303/v1/info
curl http://localhost:8303/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/positions/v1/info`.

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
