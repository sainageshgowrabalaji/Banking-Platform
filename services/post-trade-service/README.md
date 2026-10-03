# post-trade-service

Everything that happens after a trade is done.

| | |
|---|---|
| Area | Markets |
| Port | 8304 |
| Built in | Phase 3 |
| Patterns it will show | Reconciliation, saga, batch processing |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- Allocations of block trades to accounts
- The T+1 settlement cycle
- Breaks and reconciliation against the ledger
- An audit-trail export in the style of CAT reporting

## Run it

```bash
scripts/run.sh post-trade-service
curl http://localhost:8304/v1/info
curl http://localhost:8304/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/post-trade/v1/info`.

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
