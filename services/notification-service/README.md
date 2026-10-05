# notification-service

Tells customers what happened, driven by events.

| | |
|---|---|
| Area | Shared |
| Port | 8103 |
| Built in | Phase 1 |
| Patterns it shows | Event-driven consumer, idempotent consumer, retry with a dead letter topic |
| Contract | [contracts/openapi/notifications-v1.yaml](../../contracts/openapi/notifications-v1.yaml), [contracts/asyncapi/events-v1.yaml](../../contracts/asyncapi/events-v1.yaml) |

## What it does

- Listens to `bank.payments.v1` and `bank.accounts.v1` on Kafka
- Turns each event into a short message for the customer, such as "Your payment of 120.00 USD to Bob
  has been sent"
- Stores the message and writes it to the log. No email or text message is sent yet
- Answers `GET /v1/notifications?accountId=` with the latest messages of an account

No other service calls it to send a message. It only hears events, so the payments service does not
know it exists.

## Three things a consumer must get right

**Duplicates.** Kafka delivers at least once, so the same event can arrive twice. The id of every
event is stored in a `processed_event` table in the same transaction as the notification. A second
delivery finds the id and does nothing.

**A bad event.** An event that fails is tried again eight times, waiting longer each time, about two
minutes in all. That rides out a database restart. If it still fails it is moved to a dead letter topic (`bank.payments.v1.dlt`) and the consumer moves on, so one bad
event never blocks the rest. An event that cannot even be read is moved there at once, since trying
again cannot help.

**An outage.** When the database itself cannot be reached, nothing is wrong with the event. So it
is never parked. The consumer tries the same event every five seconds until the database is back.

**Order.** Events for one payment share a Kafka key, so they arrive in the order they happened.

## Run it

It needs PostgreSQL and Kafka.

```bash
scripts/run.sh notification-service
curl "http://localhost:8103/v1/notifications?accountId=<an account id>"
```

| Setting | Default | What it is |
|---|---|---|
| `NOTIFICATION_DB_URL` | `jdbc:postgresql://localhost:5432/notification` | The database |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka |

## Tests

```bash
./mvnw -pl services/notification-service -am verify
```

| Test | What it covers |
|---|---|
| `TemplatesTest` | The wording of each message |
| `ArchitectureTest` | The domain imports no framework, and nothing depends on an adapter |
| `NotificationFlowIT` | Real events on a real Kafka. One message for one event, one message for the same event sent twice, and a broken event parked without blocking the next one |

## Layout

```
domain/                    the notification and the message templates
application/               the use case and the ports it needs
adapter/in/kafka/          the Kafka listener
adapter/in/web/            the REST controller
adapter/out/persistence/   SQL that implements the ports
adapter/out/messaging/     the sender. Today it writes to the log
config/                    Spring wiring, topics and the error handler
```
