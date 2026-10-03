# Contracts

The API of each service is written here first, before the code. Reviewers, front ends and other teams read
the contract, and the code is tested against it.

| Folder | Format | What it describes |
|---|---|---|
| `openapi/` | OpenAPI 3.1 | REST endpoints, one file per service and major version |
| `asyncapi/` | AsyncAPI 3.0 | Kafka topics and the events on them |
| `proto/` | Protocol Buffers | gRPC streams between services, used where speed matters |

Every file here is a draft for the phase named in its description. The services do not serve these
endpoints yet.

## Rules every API follows

- Paths are plural nouns under a version, like `/v1/payments`
- Money is an object with a decimal string and an ISO 4217 code, like `{"amount": "125.50", "currency": "USD"}`
- Every write takes an `Idempotency-Key` header, so a retry never moves money twice
- Errors use RFC 9457 problem details with a stable `code`
- Lists use cursor pagination with `limit` and `cursor`
- Times are UTC in ISO 8601
- A breaking change gets a new version, and the old one keeps working until its clients have moved
