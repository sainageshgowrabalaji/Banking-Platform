# api-gateway

The single entry point. It routes each request to the right service.

| | |
|---|---|
| Area | Edge |
| Port | 8080 |
| Built in | Phase 1 |
| Patterns it will show | API gateway, rate limiting, circuit breaker |

## Today

Skeleton. It starts, reports health and metrics, and routes every service by path prefix.

## What gets built here

- Token checks against Keycloak, so services only see requests from signed-in users
- Rate limits per client, counted in Redis
- A request id and trace headers added to every call
- A timeout and a circuit breaker on every route

## Run it

```bash
scripts/run.sh ledger-service        # terminal 1
scripts/run.sh api-gateway           # terminal 2
curl http://localhost:8080/ledger/v1/info
```

The gateway receives `/ledger/v1/info`, removes the first path segment and forwards `/v1/info` to the ledger
service. The routes are in `src/main/resources/application.yml`.

## Layout

The gateway holds no business rules, so it has only a `config` package.
