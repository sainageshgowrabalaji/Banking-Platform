# Learning path

This project is also a course. Each topic below is something interviewers at banks and funds ask about.
For each one there is a short explanation, where it lives in this code, and the phase that builds it.

Read a topic, find it in the code, then change something and watch what happens. That is the fastest way
to be able to explain it in an interview.

| # | Topic | Where it lives | Phase |
|---|---|---|---|
| 1 | Multi-module build | `pom.xml` | Done |
| 2 | Hexagonal architecture | every `services/*/src/main/java` | Done |
| 3 | Exact money | `libs/common-money` | Done |
| 4 | Double-entry ledger | `services/ledger-service` | Done |
| 5 | API gateway | `services/api-gateway` | Done |
| 6 | Load balancing | `deploy/helm` Service and HPA | 4 |
| 7 | Service discovery | Kubernetes DNS | 4 |
| 8 | Configuration and secrets | `application.yml`, environment variables | Started, rest in 4 |
| 9 | Idempotency | `libs/common-idempotency` | Done |
| 10 | Database per service | `src/main/resources/db/migration` in each service | Done |
| 11 | Messaging and the outbox | `libs/common-messaging` | Done |
| 12 | Sagas | `PaymentSaga` in payments | Done |
| 13 | Resilience | gateway routes, `HttpLedger` in payments | Done |
| 14 | Security | gateway `SecurityConfiguration`, `infra/keycloak` | Started, rest in 2 |
| 15 | Observability | `libs/common-observability`, Grafana | Done |
| 16 | Low-latency design | trading | 2 |
| 17 | FIX and ISO 20022 | payments `iso20022` package, trading | ISO 20022 done, FIX in 2 |
| 18 | Containers and Kubernetes | `infra/docker`, `deploy/helm` | 4 |
| 19 | CI, CD and GitOps | `.github/workflows`, Argo CD | 4 |
| 20 | Cloud on AWS | `deploy/terraform` | 4 |
| 21 | Locks and concurrency | `JournalPoster` and `AccountRows` in the ledger | Done |
| 22 | Testing against real tools | `libs/common-testing`, every `*IT` class | Done |
| 23 | Contract tests | `contracts/openapi`, `Contract` in `libs/common-testing` | Done |

## 1. Multi-module build

One Maven build holds every library and service. The root `pom.xml` sets the versions once, and each
module only lists what it needs. A change to a shared library is compiled and tested against every
service in the same build, so a breaking change is caught before it is merged.

Try it. Run `./mvnw -pl services/ledger-service -am verify` and watch Maven build only the ledger and
the libraries it depends on.

## 2. Hexagonal architecture

The business rules sit in the middle and know nothing about the outside. The outside (HTTP, database,
Kafka) is plugged in through interfaces called ports, and the classes that implement them are called
adapters. You can swap PostgreSQL for another database, or REST for gRPC, without touching a rule.

Banks like this because the rules are what auditors care about, and rules that do not depend on a
framework are easy to test and hard to break by accident.

Try it. Open `services/ledger-service/.../domain/JournalEntry.java` and look at its imports. There is no
Spring in it.

## 3. Exact money

A `double` cannot hold 0.10 exactly, so `0.1 + 0.2` gives `0.30000000000000004`. A bank cannot be wrong
by a fraction of a cent, because across millions of entries it adds up and the books stop balancing.
`Money` uses `BigDecimal` at the scale of its currency, and refuses to add two different currencies.

Try it. Read `MoneyTest`. Then change `Money` to use `double` and watch the tests fail.

## 4. Double-entry ledger

Every movement of money is written twice, once as a debit and once as a credit, and the two sides must
be equal. Money can only move. It can never appear or vanish. Entries are never edited. A mistake is
fixed with a new entry that reverses the old one, so the full history can always be replayed.

A balance is the sum of every entry on that account. Adding them up on every read would be slow, so
each account row also carries a running balance that is changed in the same transaction as the entry.
A database view compares the two, and it must always be empty.

A hold is how a bank reserves money before it moves. The ledger balance stays the same, the held
amount goes up, and the available balance (what can be spent) goes down. When the payment settles,
the entry and the end of the hold happen in one transaction.

Try it. Read `JournalEntryTest` and `AccountTest`. Each test is one rule of bookkeeping. Then open
`V1__ledger.sql` and find the trigger that refuses to update a posted entry. `LedgerSafetyIT` tries
to break each rule by going straight to the database.

## 5. API gateway

Clients talk to one address. The gateway checks the token, applies rate limits, adds a request id and
forwards the call to the right service. Without it, every service would repeat that work and every
client would need to know where every service lives.

Here the gateway is Spring Cloud Gateway on the servlet stack. The routes are built in Java in
`RouteConfiguration`, one for each service, and each route gets the same four things. A rate limit
(a token bucket for each client, from Bucket4j). A circuit breaker with a timeout. A fallback that
answers 503 in the common error format. A request id.

Try it. Start phase 1 and run `scripts/demo.sh`. Then stop the ledger with
`kill $(cat .run/ledger-service.pid)` and call `curl -i http://localhost:8080/ledger/v1/info`. The
gateway answers 503 at once and does not hang. `GatewayTest` shows the same thing in a test, along
with the 429 a client gets when it goes over its limit.

## 6. Load balancing

One copy of a service is a single point of failure, so several copies run at once and requests are
spread across them. There are two places this happens.

At the edge, a cloud load balancer (an AWS Application Load Balancer) spreads internet traffic across
the gateway copies and ends TLS. Inside the cluster, a Kubernetes Service gives each microservice one
stable name and spreads calls across its pods.

Common ways to spread requests are round robin, least connections, and consistent hashing, which keeps
one customer on one copy. Health checks take a broken copy out of rotation.

## 7. Service discovery

Copies come and go, and their addresses change. Discovery is how a caller finds a live copy. Older
Spring systems ran a Eureka server that every service registered with. On Kubernetes the platform does
it. A call to `http://ledger-service` is resolved by cluster DNS to a healthy pod.

This project uses Kubernetes for discovery, which is why there is no Eureka server. On a laptop the
gateway uses fixed `localhost` addresses that environment variables can replace.

## 8. Configuration and secrets

The same build runs in every environment. Only the configuration changes, and it comes from outside the
jar as environment variables or mounted files. Secrets such as database passwords never live in git.
They come from a secret store (AWS Secrets Manager, or Vault in many banks) and are handed to the pod
at start.

Every setting here has a default that works on a laptop and an environment variable that replaces it.
`LEDGER_DB_URL`, `KAFKA_BOOTSTRAP_SERVERS` and `KEYCLOAK_ISSUER` are examples. Settings that belong
to one service are typed records such as `PaymentsProperties`, so a wrong value fails at start and
not in the middle of a payment.

Try it. Read `application.yml` in the payments service next to `PaymentsProperties`.

## 9. Idempotency

Networks fail in the middle of a request, so clients retry. Without protection, a retry sends the money
twice. The client sends an `Idempotency-Key` header, the server stores the first result under that key,
and a retry gets the same result back with no second payment.

The details matter. The key is claimed with an insert inside the same database transaction as the
business change, so the key and the change are saved together or not at all. The request body is
hashed and stored with the key, so the same key with a different body is refused. Two requests with
the same key at the same moment cannot both win, because the key is the primary key of the table.

There is a second layer under it. The ledger posts a business reference once, whatever key the
request carried. That is what lets the saga repeat a step safely.

Try it. Run step 5 and step 6 of `scripts/demo.sh` by hand. Then read `IdempotencyStore`.

## 10. Database per service

Each service has its own database and nobody else may read it. This keeps services independent, because
one team can change its tables without breaking another. The price is that there are no joins across
services and no shared transaction. Sagas and events pay that price.

Schema changes are versioned SQL files run by Flyway at start, so the database and the code always
match. Hibernate is set to `validate`, which means it never changes a table. It only checks that its
classes match what Flyway built.

Try it. Read `V1__ledger.sql`. Then add a column in a `V3` file, start the service and look at the
`flyway_schema_history` table.

## 11. Messaging and the outbox

Services announce facts as events on Kafka. Events for one account share a key, so they stay in order.

The hard problem is the dual write. A service must save to its database and publish an event, and a
crash between the two leaves them out of step. The outbox pattern solves it. The event is saved in an
outbox table in the same database transaction as the business data, and a separate relay publishes it
to Kafka. Delivery is then at least once, so every consumer must skip duplicates by event id.

Here `Outbox.publish` refuses to run outside a transaction, so nobody can forget. `OutboxRelay` sends
rows in order, and takes a PostgreSQL advisory lock so only one copy of a service relays at a time.
On the other side, `ProcessedEvents` stores each event id in the same transaction as the consumer's
own work. An event that cannot be handled is retried for about two minutes, waiting longer each
time, and then moved to a dead letter topic, so one bad event does not block the rest.

Try it. `NotificationFlowIT` sends the same event twice and checks that one notification comes out.

## 12. Sagas

A payment touches several services and there is no transaction that spans them. A saga is a chain of
local steps where each step has an undo. If screening fails after the hold was placed, the saga releases
the hold. The payment always ends in a clear final state.

There are two styles. In an orchestrated saga one class knows the whole route and calls each step. In
a choreographed saga each service reacts to the events of the others and nobody is in charge. This
project uses orchestration, because with money it matters most that a person can read the route in
one place.

The saga state lives in the payment row. A step claims the row, calls one other system, and saves the
result. A background worker picks up any payment that still needs work, so a crash or a ledger outage
only delays a payment. It never loses one.

Two details are worth reading closely. Before the rail is called, the saga first saves that the
payment is leaving, and only a payment not yet marked that way can be cancelled. And once a rail
has paid out, the payment can no longer be answered with a rejection. If the ledger then refuses to
post it, the payment is parked for a person, because telling the customer "no money left your
account" would be false.

Try it. Read `PaymentSaga` from the top. Then read `whenTheLedgerIsDownThePaymentIsKeptAndFinishedLater`
in `PaymentFlowsIT`.

## 13. Resilience

In a distributed system something is always slow or down. The standard tools are a timeout on every
call, retries with backoff for safe operations, a circuit breaker that stops calling a failing service
for a while, and bulkheads that keep one slow dependency from using up every thread. Resilience4j
provides these in Java.

A circuit breaker has three states. Closed lets calls through and counts failures. Open refuses calls
at once, so a failing service is not hit again and again and the caller does not wait. Half open lets
a few calls through to see if the service is back.

The gateway puts a breaker and a timeout on every route. The payments service puts a breaker and two
timeouts on its calls to the ledger, and the saga retries with a growing wait (2, 4, 8 and up to 60
seconds). A retry is only safe because every step is idempotent. That is why topic 9 comes first.

Try it. Stop the ledger, send a payment and watch it stay RCVD. Start the ledger and watch it settle.

## 14. Security

Users sign in with Keycloak using OpenID Connect and get a signed token (a JWT). The gateway checks the
signature and the services check the roles and scopes inside it. Between services, mutual TLS proves
which service is calling. Every sensitive action is written to an audit log that cannot be edited.

Today the gateway checks the signature, the issuer and the expiry against Keycloak's public keys. It
never calls Keycloak for a request and never sees a password. Roles come from the `realm_access`
claim. Endpoints that move money directly in the ledger need the `bank-operator` role, so a customer
can never post a journal entry however valid the token is. The services behind the gateway do not
check tokens yet. That, mutual TLS and the audit log come in later phases.

Try it. `scripts/token.sh alice` prints a customer token. Paste it into jwt.io and read the claims.
Then call a journal endpoint with it and see the 403.

## 15. Observability

Three signals show what a running system is doing. Metrics are numbers over time, such as requests per
second. Logs are the detail of single events. Traces follow one request across every service it
touched. OpenTelemetry collects all three, and Grafana shows them.

Set `OTEL_EXPORTER_OTLP_ENDPOINT` and a service sends all three to that collector. Leave it out and
nothing is sent, but trace ids still appear in the log lines and Prometheus can still read
`/actuator/prometheus`.

A trace crosses HTTP in the `traceparent` header. It crosses Kafka because the outbox saves the trace
context with the event and the relay sends it as a Kafka header. So a payment is one trace from the
gateway to the notification. The services also count what the business cares about, such as
`bank.payments` by rail and status and `bank.outbox.waiting`.

Try it. Start phase 1 with `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`, run the demo, open
Grafana on http://localhost:3000, go to Explore, pick Tempo and search for the service
`api-gateway`. Open a POST to `/payments/**` and look at the spans.

## 16. Low-latency design

A matching engine must handle orders in microseconds and in a strict order. The usual design is a single
thread that owns the order book and reads orders from a ring buffer (the LMAX Disruptor). One writer
means no locks, and no locks means steady speed. Every order is also written to a journal first, so the
book can be rebuilt after a crash by replaying it.

## 17. FIX and ISO 20022

These are the two message standards of finance. FIX is how trading systems send orders and executions
to each other. ISO 20022 is how banks describe payments, and it is the format behind SWIFT, FedNow and
the instant payment networks. Knowing the main messages is a real advantage in an interview.

The payments service reads and writes three ISO 20022 messages. A pain.001 is what a company sends
its bank to start a payment. A pacs.008 is what one bank sends another to move the money. A pacs.002
is the answer, with a status and a reason code. The status codes of a payment here (RCVD, ACCP, ACSP,
ACSC, RJCT, CANC) and the reason codes (AM04 for not enough money, AC04 for a closed account) are the
real ISO 20022 codes.

Try it. Run step 7 of the demo, then call `GET /payments/v1/payments/{id}/messages` and read the
XML. `Iso20022Test` shows each message being written and read back.

## 18. Containers and Kubernetes

Each service is packed into a container image that runs the same everywhere. Kubernetes runs the
images, restarts them when they fail, adds copies under load and rolls out new versions with no
downtime. Liveness and readiness probes tell it when a pod is broken or not ready yet.

## 19. CI, CD and GitOps

Continuous integration builds and tests every push. Continuous delivery turns a passing build into an
image and a deployment. With GitOps the desired state of the cluster is a folder in git, and a tool
(Argo CD) keeps the cluster equal to it. A rollback is a revert of one commit.

## 20. Cloud on AWS

The cloud pieces map directly to what runs on the laptop. EKS runs Kubernetes, RDS runs PostgreSQL, MSK
runs Kafka, and an Application Load Balancer sits in front. Terraform describes all of it as code, so
an environment can be created, reviewed and destroyed like any other change.

## 21. Locks and concurrency

Two payments from the same account at the same moment must not both spend the same money. There are
two common answers. An optimistic lock lets both try and makes the second one fail on a version
number, then retry. A pessimistic lock makes the second one wait for the first.

The ledger takes pessimistic row locks (`select ... for update`) on every account an entry touches,
always in id order. The fixed order is what prevents a deadlock, where A waits for B while B waits
for A. A version column is kept as a second guard, and the database refuses a negative customer
balance as a third.

Try it. `manyPaymentsAtOnceNeverOverdrawAnAccount` in `LedgerSafetyIT` sends 40 payments at once
from an account that can afford only some of them.

## 22. Testing against real tools

A test that replaces the database with a fake proves little about locks, triggers or SQL. So the
integration tests (the classes named `*IT`) start the real service on a real port and give it a real
PostgreSQL and a real Kafka in Docker, through Testcontainers. Each test class gets its own empty
database.

They run in the `verify` phase, after the fast unit tests. With no Docker they are skipped and say
why. Where Docker is not allowed, the variables `BANK_TEST_POSTGRES_URL` and `BANK_TEST_KAFKA` point
them at servers that are already running.

Try it. Run `./mvnw -pl services/ledger-service -am verify` with Docker on, then with Docker off.

## 23. Contract tests

Other teams build against the files in `contracts/`. A contract that the code has drifted away from
is worse than none. So every response the integration tests receive is checked against the OpenAPI
file. A renamed field, a status code nobody wrote down or an endpoint missing from the file fails the
build.

Try it. Rename a field in `Dtos` in the ledger service, run the integration tests and read the
failure.
