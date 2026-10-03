# Shared libraries

Small, stable code that every service uses. A library here must stay free of business rules, because a
change to it rebuilds every service.

| Library | What it holds |
|---|---|
| `common-money` | `Money`, an exact decimal amount with its currency |
| `common-events` | `EventEnvelope`, the wrapper around every Kafka event, and the topic names |
| `common-web` | `IdempotencyKey` and `Problems`, the shared error format |

`common-money` and `common-events` are plain Java with no dependencies. `common-web` uses only the HTTP
types from Spring.
