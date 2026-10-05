# Contracts

The API of each service is written here first, before the code. Reviewers, front ends and other teams read
the contract, and the code is tested against it.

| Folder | Format | What it describes |
|---|---|---|
| `openapi/` | OpenAPI 3.1 | REST endpoints, one file per service and major version |
| `asyncapi/` | AsyncAPI 3.0 | Kafka topics and the events on them |
| `proto/` | Protocol Buffers | gRPC streams between services, used where speed matters |

## What is live

| File | State |
|---|---|
| `openapi/ledger-v1.yaml` | Served by the ledger service |
| `openapi/payments-v1.yaml` | Served by the payments service |
| `openapi/notifications-v1.yaml` | Served by the notification service |
| `asyncapi/events-v1.yaml` | The `bank.*` topics are live. The `markets.*` topic is a draft |
| `openapi/trading-v1.yaml`, `proto/` | Drafts for phase 2 |

## How the code is held to the contract

Every response the integration tests receive is compared with the OpenAPI file, by `Contract` in
`libs/common-testing`. The check is strict. A field the file does not list, a status code nobody
wrote down or a path missing from the file fails the build. So a change to an API starts here, in
the contract, and the code follows.

CI also checks that each OpenAPI file is valid on its own.

## Rules every API follows

- Paths are plural nouns under a version, like `/v1/payments`
- Money is an object with a decimal string and an ISO 4217 code, like `{"amount": "125.50", "currency": "USD"}`
- Every write takes an `Idempotency-Key` header, so a retry never moves money twice
- Errors use RFC 9457 problem details with a stable `code`
- Lists use cursor pagination with `limit` and `cursor`
- Times are UTC in ISO 8601
- A breaking change gets a new version, and the old one keeps working until its clients have moved
