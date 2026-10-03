# wealth-service

Managed portfolios for private clients.

| | |
|---|---|
| Area | Wealth |
| Port | 8401 |
| Built in | Phase 3 |
| Patterns it will show | Strategy, domain service, anti-corruption layer |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- Model portfolios with target weights
- Drift checks and rebalancing that sends orders to trading
- Time-weighted returns

## Run it

```bash
scripts/run.sh wealth-service
curl http://localhost:8401/v1/info
curl http://localhost:8401/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/wealth/v1/info`.

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
