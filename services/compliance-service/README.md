# compliance-service

The checks a regulator expects before and after money moves.

| | |
|---|---|
| Area | Control |
| Port | 8201 |
| Built in | Phase 2 |
| Patterns it will show | Rule engine, specification, chain of responsibility, audit log |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- KYC onboarding with risk rating
- Sanctions screening with fuzzy name matching
- AML rules over transaction history
- Cases for analysts, with a full audit log
- Trade surveillance alerts in phase 3

## Run it

```bash
scripts/run.sh compliance-service
curl http://localhost:8201/v1/info
curl http://localhost:8201/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/compliance/v1/info`.

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
