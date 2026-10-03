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
| 4 | Double-entry ledger | `services/ledger-service` domain | Started |
| 5 | API gateway | `services/api-gateway` | Started |
| 6 | Load balancing | `deploy/helm` Service and HPA | 4 |
| 7 | Service discovery | Kubernetes DNS | 4 |
| 8 | Configuration and secrets | `application.yml`, environment variables | 1 and 4 |
| 9 | Idempotency | `libs/common-web`, payments | 1 |
| 10 | Database per service | `infra/postgres/init` | 1 |
| 11 | Messaging and the outbox | `libs/common-events`, ledger, payments | 1 |
| 12 | Sagas | payments | 1 |
| 13 | Resilience | gateway and service clients | 1 |
| 14 | Security | gateway, Keycloak | 1 |
| 15 | Observability | every service, Grafana | 1 |
| 16 | Low-latency design | trading | 2 |
| 17 | FIX and ISO 20022 | trading, payments | 1 and 2 |
| 18 | Containers and Kubernetes | `infra/docker`, `deploy/helm` | 4 |
| 19 | CI, CD and GitOps | `.github/workflows`, Argo CD | 4 |
| 20 | Cloud on AWS | `deploy/terraform` | 4 |

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

A balance is not a number in a column. It is the sum of every entry on that account.

Try it. Read `JournalEntryTest`. Each test is one rule of bookkeeping.

## 5. API gateway

Clients talk to one address. The gateway checks the token, applies rate limits, adds a request id and
forwards the call to the right service. Without it, every service would repeat that work and every
client would need to know where every service lives.

Here the gateway is Spring Cloud Gateway on the servlet stack. Today it only routes. Tokens, rate limits
and circuit breakers arrive in phase 1.

Try it. Start the ledger and the gateway, then call `curl -i http://localhost:8080/ledger/v1/info`.
Then stop the ledger and call again to see what a client gets when a service is down.

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

## 9. Idempotency

Networks fail in the middle of a request, so clients retry. Without protection, a retry sends the money
twice. The client sends an `Idempotency-Key` header, the server stores the first result under that key,
and a retry gets the same result back with no second payment.

## 10. Database per service

Each service has its own database and nobody else may read it. This keeps services independent, because
one team can change its tables without breaking another. The price is that there are no joins across
services and no shared transaction. Sagas and events pay that price.

Schema changes are versioned SQL files run by Flyway at start, so the database and the code always
match.

## 11. Messaging and the outbox

Services announce facts as events on Kafka. Events for one account share a key, so they stay in order.

The hard problem is the dual write. A service must save to its database and publish an event, and a
crash between the two leaves them out of step. The outbox pattern solves it. The event is saved in an
outbox table in the same database transaction as the business data, and a separate relay publishes it
to Kafka. Delivery is then at least once, so every consumer must skip duplicates by event id.

## 12. Sagas

A payment touches several services and there is no transaction that spans them. A saga is a chain of
local steps where each step has an undo. If screening fails after the hold was placed, the saga releases
the hold. The payment always ends in a clear final state.

## 13. Resilience

In a distributed system something is always slow or down. The standard tools are a timeout on every
call, retries with backoff for safe operations, a circuit breaker that stops calling a failing service
for a while, and bulkheads that keep one slow dependency from using up every thread. Resilience4j
provides these in Java.

## 14. Security

Users sign in with Keycloak using OpenID Connect and get a signed token (a JWT). The gateway checks the
signature and the services check the roles and scopes inside it. Between services, mutual TLS proves
which service is calling. Every sensitive action is written to an audit log that cannot be edited.

## 15. Observability

Three signals show what a running system is doing. Metrics are numbers over time, such as requests per
second. Logs are the detail of single events. Traces follow one request across every service it
touched. OpenTelemetry collects all three, and Grafana shows them.

Every service already exposes `/actuator/health` and `/actuator/prometheus`.

## 16. Low-latency design

A matching engine must handle orders in microseconds and in a strict order. The usual design is a single
thread that owns the order book and reads orders from a ring buffer (the LMAX Disruptor). One writer
means no locks, and no locks means steady speed. Every order is also written to a journal first, so the
book can be rebuilt after a crash by replaying it.

## 17. FIX and ISO 20022

These are the two message standards of finance. FIX is how trading systems send orders and executions
to each other. ISO 20022 is how banks describe payments, and it is the format behind SWIFT, FedNow and
the instant payment networks. Knowing the main messages is a real advantage in an interview.

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
