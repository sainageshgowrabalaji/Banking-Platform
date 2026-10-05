# Banking Platform

[![CI](https://github.com/sainageshgowrabalaji/Banking-Platform/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/sainageshgowrabalaji/Banking-Platform/actions/workflows/ci.yml)

One platform that covers the main lines of business of a bank or finance firm. Retail banking and
payments, trading, risk, post-trade, wealth management and compliance, built as Java microservices on
one shared core.

The stack follows what finance firms in New York ask for most in their job postings, which is Java,
Spring Boot, Kafka, PostgreSQL, Kubernetes and AWS, with FIX and ISO 20022 as the industry message
standards.

## Status

**Phase 1, core banking, is built. October 2026.** Money moves end to end. A client sends a payment to
the gateway, the payments service reserves the money in the ledger, sends the payment on its rail,
settles it, and the customer is told. Phases 2 to 4 are still skeletons. This table says exactly what
exists today.

| Part | Today |
|---|---|
| Ledger | Working. Accounts, holds, journal posting and balances on PostgreSQL, with Flyway migrations. The database itself refuses an entry that does not balance, an overdrawn customer account, and any edit of a posted entry |
| Payments | Working. A saga with book, ACH and instant rails. The rails are simulated. Messages are real ISO 20022 (pain.001 in, pacs.008 out, pacs.002 back) |
| Events | Working. Each service writes its events to an outbox table in the same transaction as the business change, and a relay sends them to Kafka |
| Idempotency | Working. A retried request with the same `Idempotency-Key` gets the first result and moves no money twice |
| Notifications | Working. A Kafka consumer that skips duplicates and parks an event it cannot handle on a dead letter topic |
| API gateway | Working. Keycloak tokens, a rate limit per client, and a timeout and circuit breaker on every route |
| Observability | Working. Traces, metrics and logs go to Grafana over OpenTelemetry. One trace follows a payment through all four services, across HTTP and Kafka |
| Tests | 117 unit and architecture tests that need nothing but Java. 61 integration tests against a real PostgreSQL and a real Kafka, where every response is also checked against the API contract |
| API contracts | Ledger, payments and notifications are served as written. Trading and market data are drafts for phase 2 |
| Other 7 services | Skeletons. Each one starts, reports health and answers `GET /v1/info` |
| Deployment | A Helm chart and Terraform files as stubs. Nothing has been deployed |

What phase 1 does not do yet is listed in the [roadmap](docs/roadmap.md), next to what each later
phase adds.

## Architecture

```mermaid
flowchart TB
    C[Web app, API clients, FIX clients] --> GW[api-gateway]

    subgraph banking[Banking]
        L[ledger-service]
        P[payments-service]
    end
    subgraph markets[Markets]
        MD[market-data-service]
        T[trading-service]
        PO[positions-service]
        PT[post-trade-service]
    end
    subgraph wealth[Wealth]
        W[wealth-service]
    end
    subgraph control[Control and shared]
        CO[compliance-service]
        N[notification-service]
        OA[ops-agent]
    end

    GW --> L & P & T & W & CO
    P -->|hold and post| L
    P -.->|screen, phase 2| CO
    W -->|orders| T
    MD -->|prices| T
    L & P & T -.->|events| K[(Kafka)]
    K -.-> N & PO & PT & CO
    PT -->|cash movements| L
```

Solid lines are direct calls. Dotted lines are events. Each service owns its own database, and no
service reads the database of another.

More detail is in [docs/architecture.md](docs/architecture.md).

## Services

| Service | Area | Port | Phase | What it does |
|---|---|---|---|---|
| [api-gateway](services/api-gateway) | Edge | 8080 | 1 | The single entry point. It routes each request to the right service. |
| [ledger-service](services/ledger-service) | Banking | 8101 | 1 | Accounts, holds and the double-entry journal. Every balance is the sum of entries that never change. |
| [payments-service](services/payments-service) | Banking | 8102 | 1 | The life of a payment, from request to settlement. |
| [notification-service](services/notification-service) | Shared | 8103 | 1 | Tells customers what happened, driven by events. |
| [compliance-service](services/compliance-service) | Control | 8201 | 2 | The checks a regulator expects before and after money moves. |
| [market-data-service](services/market-data-service) | Markets | 8301 | 2 | Prices for every instrument the platform trades. |
| [trading-service](services/trading-service) | Markets | 8302 | 2 | Takes orders, checks them and matches them. |
| [positions-service](services/positions-service) | Markets | 8303 | 3 | What each account holds and what it is worth. |
| [post-trade-service](services/post-trade-service) | Markets | 8304 | 3 | Everything that happens after a trade is done. |
| [wealth-service](services/wealth-service) | Wealth | 8401 | 3 | Managed portfolios for private clients. |
| [ops-agent](services/ops-agent) | Shared | 8501 | 4 | An AI agent that helps the people who run the platform. |

Shared code lives in [libs](libs).

## Run it

You need JDK 21 or newer.

```bash
java -version                    # must say 21 or newer
./mvnw verify                    # builds every module and runs every test
```

If Java is older than 21, install it with `brew install --cask temurin@21`, or download it from
adoptium.net. The first build downloads Maven and the libraries, so it takes a few minutes.

The unit tests need nothing else. The integration tests start PostgreSQL and Kafka in Docker. If
Docker is not running they are skipped, and the build still passes. GitHub Actions has Docker, so
every push runs all of them.

### See a payment move

This needs Docker for PostgreSQL, Kafka, Keycloak and Grafana.

```bash
docker compose -f infra/docker-compose.yml up -d     # the tools
scripts/phase1.sh start                              # gateway, ledger, payments, notification
TOKEN=$(scripts/token.sh olivia) scripts/demo.sh     # 31 checks, each printed next to what was expected
scripts/phase1.sh stop
```

`scripts/demo.sh` opens two accounts, pays money in, sends a book payment, an instant payment and an
ACH payment, tries the things that must be refused, and reads the notifications. It is the fastest
way to see what the platform does. Read it, it is plain `curl`.

To see the traces, start the services with the collector address and open Grafana on
http://localhost:3000.

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 scripts/phase1.sh start
```

With no Keycloak, start with `GATEWAY_SECURITY=false scripts/phase1.sh start` and run
`scripts/demo.sh` with no token.

## What is where

```
libs/         shared code (money, events, web, messaging, idempotency, observability, testing)
services/     one folder per microservice, each with its own README
contracts/    the APIs other teams build against (OpenAPI, AsyncAPI, proto)
infra/        Docker Compose for the local tools, the Keycloak realm, the shared Dockerfile
deploy/       Helm chart and Terraform for the cloud
docs/         architecture, learning path, glossary, roadmap, decisions
scripts/      start phase 1, the demo, a token, run one service, smoke test
```

## Design rules already in the code

- Money is never a floating point number. It is an exact decimal with its currency
- Money can only move. A journal entry cannot exist unless its debits equal its credits, and the
  database checks it again on commit
- A posted entry is never changed. A mistake is fixed with a new entry
- Every write takes an `Idempotency-Key`, so a retry is always safe
- A business change and its event are saved in one transaction, so an event is never lost and never
  sent for a change that did not happen
- A payment always ends in a clear final status. A step that fails is undone, and a step that could
  not be tried is tried again later
- Business rules live in a `domain` package with no framework imports, and an architecture test
  fails the build if that is broken
- Each service owns its database, and no service reads the database of another
- Errors have one format across all services (RFC 9457 problem details) with a stable `code`
- Every response in the integration tests is checked against the API contract

## Docs

| Doc | What it covers |
|---|---|
| [Architecture](docs/architecture.md) | The services, how a payment flows, who owns which data |
| [Learning path](docs/learning-path.md) | Gateway, load balancing, discovery, resilience, messaging, security, cloud, and where each one lives in this code |
| [Glossary](docs/glossary.md) | Banking, payments, trading and wealth terms in plain words |
| [Roadmap](docs/roadmap.md) | What each phase adds |
| [Decisions](docs/adr) | Why the project is built the way it is |

## Not real money

This is a learning and portfolio project. It does not connect to a real bank, a real payment network or
a real exchange. Rails, prices and counterparties are simulated.

Built by Sai Nagesh Gowra Balaji.
