# ops-agent

An AI agent that helps the people who run the platform.

| | |
|---|---|
| Area | Shared |
| Port | 8501 |
| Built in | Phase 4 |
| Patterns it will show | Tool-using agent, guardrails, evaluation sets |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`.

## What gets built here

- Read-only tools over logs, traces, test reports and scan reports
- Answers that link to the evidence they came from
- No tool that can move money or change data

## Run it

```bash
scripts/run.sh ops-agent
curl http://localhost:8501/v1/info
curl http://localhost:8501/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/ops-agent/v1/info`.

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
