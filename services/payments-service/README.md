# payments-service

The life of a payment, from request to settlement.

| | |
|---|---|
| Area | Banking |
| Port | 8102 |
| Built in | Phase 1 |
| Patterns it shows | Orchestrated saga, state machine, strategy per rail, idempotent receiver, circuit breaker, transactional outbox |
| Contract | [contracts/openapi/payments-v1.yaml](../../contracts/openapi/payments-v1.yaml) |

## What it does

- Takes a payment as JSON or as an ISO 20022 pain.001 message
- Runs the payment saga (reserve the money, screen, send on the rail, settle or give the money back)
- Sends on three rails (book, ACH and instant), all simulated
- Lets a payment be cancelled until it has been sent
- Keeps the ISO 20022 messages it exchanged, so they can be read back
- Publishes `bank.payments.accepted`, `settled`, `rejected` and `cancelled` through its outbox

## The saga

| Step | Status after it | Undo |
|---|---|---|
| Received, saved with its Idempotency-Key | RCVD | |
| Reserve the money with a hold in the ledger | RCVD | Release the hold |
| Screen the receiver | ACCP | |
| Write down that the payment is leaving | ACSP | From here it cannot be cancelled |
| Send on the rail | ACSP | A refusal means it never left |
| Post the entry that moves the money and ends the hold | ACSC | None. This is the point of no return |

A failure ends the payment as RJCT with an ISO 20022 reason code, and the hold is released. The
status and reason codes are the real ISO 20022 ones.

"Leaving" is saved before the rail is called, on purpose. If it were saved after, a payment could be
cancelled in the moment between the rail taking it and this service writing that down, and the
money would leave with nothing debited.

`PaymentSaga` is the one class that knows this route. The state lives in the `payment` row, so a
restart loses nothing. The request thread takes as many steps as it can before answering, and
`PaymentSagaWorker` finishes the rest in the background.

## When a step fails

| What went wrong | What the saga does |
|---|---|
| The ledger or the database is down or busy | Waits and tries again after 2, 4, 8 and up to 60 seconds, for as long as it takes |
| A rule refused the payment before it left (no money, closed account, blocked name) | RJCT with the reason, and the hold is released |
| The rail has paid out, and then the ledger refuses to post the entry | Never RJCT, because the receiver has the money. The payment is parked |
| The instant network answers something that is neither a yes nor a no | Never RJCT, because the receiver may still be paid. The saga asks again, and after ten tries the payment is parked |
| Anything else, ten times in a row | The payment is parked |

A parked payment keeps its status and its hold, leaves the worker's list, and is counted by the
`bank.payments.parked` metric, which should be zero. A person finds the cause in `last_error`, fixes
it, and lets the saga try again.

```sql
select id, status, last_error from payment where parked_at is not null;
update payment set parked_at = null, failures = 0, attempts = 0, next_attempt_at = now() where id = '...';
```

Cancelling a payment that is still RCVD has one more twist. The ledger may have reserved the money
in a call whose answer never arrived, so the payment has no hold id. The undo step asks the ledger
for the same hold again, which returns the one it has, and then releases it.

## The rails

Each rail is one class behind the `RailGateway` port, so adding a rail means adding a class.

| Rail | Class | How it behaves |
|---|---|---|
| BOOK | `BookRail` | Settles at once. The receiver is another account in this bank |
| INSTANT | `InstantRail` | Writes a pacs.008, sends it to a simulated network and reads the pacs.002 answer. Has an amount limit |
| ACH | `AchRail`, `AchBatchJobs` | Queues the payment, cuts a batch on a timer and settles the batch later. After the cut-off it moves to the next business day |

To try each outcome, the simulated receiving banks treat an account number ending in 0000 as closed
(AC04), 1111 as unknown (AC01) and 2222 as blocked (AC06). A receiver whose name contains "blocked
person" is stopped by screening (RR04).

## Run it

It needs PostgreSQL, Kafka and the ledger service.

```bash
scripts/run.sh payments-service
curl http://localhost:8102/v1/info
```

`scripts/demo.sh` sends one payment on each rail through the gateway.

| Setting | Default | What it is |
|---|---|---|
| `PAYMENTS_DB_URL` | `jdbc:postgresql://localhost:5432/payments` | The database |
| `LEDGER_URL` | `http://localhost:8101` | Where the ledger is |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |
| `bank.payments.ach.batch-interval` | 15 seconds | How often a batch is cut. Hours in real life |
| `bank.payments.ach.settle-after` | 15 seconds | How long a batch takes to settle. A day or two in real life |
| `bank.payments.ach.cut-off` | 17:00 Eastern | After this a payment waits for the next business day |
| `bank.payments.instant.limit` | 500000.00 | The largest instant payment |
| `bank.payments.park-after` | 10 | How many unexpected failures in a row park a payment |

## Tests

```bash
./mvnw -pl services/payments-service -am verify
```

| Test | What it covers |
|---|---|
| `PaymentTest` | The state machine. Which status can follow which |
| `BusinessCalendarTest` | Cut-off times, weekends and bank holidays |
| `Iso20022Test` | Each message written and read back, and a hostile XML file refused |
| `PaymentFlowsIT` also covers | A hold whose answer was lost, a payment parked and then repaired, an ISO message with a trick amount |
| `SimulatorsTest` | The simulated receiving banks |
| `ArchitectureTest` | The domain imports no framework, and nothing depends on an adapter |
| `PaymentFlowsIT` | Every flow against a real PostgreSQL and Kafka, with a stand-in ledger that can be switched off. Each response is checked against the contract |

## Layout

This service uses plain SQL through Spring's `JdbcClient`, where the ledger uses JPA. Both styles
are common in banks.

```
domain/                    the payment state machine, the business calendar
application/               the saga, the use cases and the ports they need
iso20022/                  pain.001, pacs.008 and pacs.002, written and read with the JDK's own XML code
adapter/in/web/            REST controllers
adapter/out/persistence/   SQL that implements the ports
adapter/out/ledger/        the HTTP client of the ledger, with timeouts and a circuit breaker
adapter/out/rail/          one class per rail, and the simulators
adapter/out/screening/     the blocked names check
adapter/out/messaging/     events, written to the outbox
config/                    Spring wiring and settings
```
