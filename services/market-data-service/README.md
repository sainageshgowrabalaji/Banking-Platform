# market-data-service

Prices for every instrument the platform trades.

| | |
|---|---|
| Area | Markets |
| Port | 8301 |
| Built in | Phase 2 |
| Patterns it will show | Publish and subscribe, streaming, time-series storage |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- A simulated price feed for a small set of symbols
- Top of book and depth over a gRPC stream
- Tick history in QuestDB

## Run it

```bash
scripts/run.sh market-data-service
curl http://localhost:8301/v1/info
curl http://localhost:8301/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/market-data/v1/info`.

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
