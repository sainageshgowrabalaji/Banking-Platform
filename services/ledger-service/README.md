# ledger-service

Accounts, holds and the double-entry journal. Every balance is the sum of entries that never change.

| | |
|---|---|
| Area | Banking |
| Port | 8101 |
| Built in | Phase 1 |
| Patterns it will show | Double-entry ledger, aggregate, optimistic locking, transactional outbox |

## Today

Skeleton. It starts, reports health and metrics, and answers `GET /v1/info`. The `domain` package already holds the core rule of the ledger with tests. A `JournalEntry` cannot be built unless its debits equal its credits.

## What gets built here

- Accounts with a status lifecycle (pending, active, frozen, closed)
- Holds that reserve money before a payment settles
- Journal posting inside one database transaction, with optimistic locking
- Ledger and available balances
- Events through a transactional outbox
- The database schema as Flyway migrations

## Run it

```bash
scripts/run.sh ledger-service
curl http://localhost:8101/v1/info
curl http://localhost:8101/actuator/health
```

Through the gateway the same call is `curl http://localhost:8080/ledger/v1/info`.

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
