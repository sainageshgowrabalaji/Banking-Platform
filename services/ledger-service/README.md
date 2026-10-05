# ledger-service

Accounts, holds and the double-entry journal. Every balance is the sum of entries that never change.

| | |
|---|---|
| Area | Banking |
| Port | 8101 |
| Built in | Phase 1 |
| Patterns it shows | Double-entry ledger, aggregate, pessimistic and optimistic locking, transactional outbox, idempotent receiver |
| Contract | [contracts/openapi/ledger-v1.yaml](../../contracts/openapi/ledger-v1.yaml) |

## What it does

- Opens accounts and moves them through their statuses (active, frozen, closed)
- Places holds that reserve money before a payment settles, and releases them
- Posts balanced journal entries. Each one is a single database transaction
- Answers the ledger, held and available balance of an account
- Lists the entries that touched an account, newest first, in pages
- Publishes `bank.accounts.opened` and `bank.ledger.entry-posted` through its outbox

## The rules, and who enforces them

| Rule | Domain code | Database |
|---|---|---|
| Debits equal credits | `JournalEntry` cannot be built otherwise | A trigger checks every entry on commit |
| A customer account is never overdrawn | `Account.post` refuses the debit | A check constraint on the balance |
| Held money cannot be spent by anything else | `Account` counts holds against what is available | The same check constraint |
| A posted entry is never changed | There is no code path that edits one | Triggers refuse update and delete |
| A reference is posted once | `JournalPoster` returns the first entry for a repeat | A unique index |
| A reference holds money once | `PlaceHold` returns the hold while it is active, and refuses once it has ended | A unique index |
| A hold is ended only by the entry that spends it | `JournalPoster` checks that the entry debits the held account by exactly the held amount | |
| The bank's own accounts are never frozen or closed | `Account.freeze` and `Account.close` | |
| The journal cannot be emptied | | Triggers refuse `truncate` |
| The bank's own accounts are not opened through the API | `AccountController` refuses the INTERNAL type | They come from a migration |
| A frozen account receives but does not send | `Account.post` | |
| Only an empty account can be closed | `Account.close` | |

The database rules are a second wall. They hold even if someone connects with a SQL client, and
`LedgerSafetyIT` proves it by trying.

## How posting stays correct under load

`JournalPoster` locks every account the entry touches with `select ... for update`, always in id
order. Two transfers between the same pair of accounts therefore queue up and cannot deadlock. Each
account row carries a version number as a second guard.

Everything else is read after the locks are held. That order matters. Two requests for the same
reference at the same moment then take turns, and the second one sees what the first one did. If
the reference were looked up first, both could find nothing and both would go on.

If a lock cannot be taken in 5 seconds the whole attempt is rolled back and tried again, up to 6 times in all, and after that the caller gets 409 with
`Retry-After`.

## Run it

It needs PostgreSQL and Kafka. `docker compose -f infra/docker-compose.yml up -d` starts both.

```bash
scripts/run.sh ledger-service
curl http://localhost:8101/v1/info

curl -X POST http://localhost:8101/v1/accounts \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-key-0001' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","type":"CHECKING","currency":"USD"}'
```

`scripts/demo.sh` shows the rest through the gateway.

| Setting | Default | What it is |
|---|---|---|
| `LEDGER_DB_URL` | `jdbc:postgresql://localhost:5432/ledger` | The database |
| `LEDGER_DB_USER`, `LEDGER_DB_PASSWORD` | `bank`, `bank_local_only` | For a laptop only |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `bank.ledger.hold-lifetime` | 7 days | When a hold nobody used is given back |
| `bank.ledger.lock-timeout` | 5 seconds | How long a posting waits for an account another posting has locked |

The bank's own accounts (cash, the ACH settlement account and the instant settlement account) are
created by the `V2` migration with fixed ids, so other services can name them.

## Tests

```bash
./mvnw -pl services/ledger-service -am verify
```

| Test | What it covers |
|---|---|
| `JournalEntryTest`, `AccountTest`, `HoldTest` | The rules, in plain Java, in milliseconds |
| `ArchitectureTest` | The domain imports no framework, and nothing depends on an adapter |
| `LedgerApiIT` | Every endpoint against a real PostgreSQL, with each response checked against the contract |
| `LedgerSafetyIT` | 40 payments at once, transfers in both directions at once, the same request sent eight times at once, and direct attacks on the database rules |
| `LedgerLockTimeoutIT` | An account that stays locked. The posting gives up cleanly and works on the next try |

## Layout

Every service uses the same hexagonal layout, so the business rules never depend on a framework.

```
domain/                    business rules in plain Java, no Spring
application/               use cases and the ports (interfaces) they need
adapter/in/web/            REST controllers
adapter/out/persistence/   JPA code that implements the ports
adapter/out/messaging/     events, written to the outbox
config/                    Spring wiring
```
