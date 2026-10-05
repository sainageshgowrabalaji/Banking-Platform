# api-gateway

The single entry point. It routes each request to the right service.

| | |
|---|---|
| Area | Edge |
| Port | 8080 |
| Built in | Phase 1 |
| Patterns it shows | API gateway, token validation, rate limiting, circuit breaker, timeout, fallback |

## What it does

A request to `/ledger/v1/accounts` is checked, then sent to the ledger service as `/v1/accounts`.
Every route gets the same treatment.

| Step | What happens | If it fails |
|---|---|---|
| Request id | An `X-Request-Id` is added if the client sent none, passed on, and returned | |
| Token | The signature, issuer and expiry of the Keycloak token are checked | 401 |
| Role | Endpoints that move money directly in the ledger, and the metrics of every service, need the `bank-operator` role | 403 |
| Rate limit | Each client gets 120 requests a minute, as a token bucket | 429 with `Retry-After` |
| Circuit breaker and timeout | A service that is down or slower than 10 seconds is given up on | 503 from the fallback |

Every error has the same problem details format as the services behind it.

The circuit breaker counts calls that could not connect or took too long. An error answer from a
service, such as a 422, is passed on to the client as it is, with its own code and trace id.

The token check needs no call to Keycloak. The gateway fetches Keycloak's public keys once and
checks each signature itself. Health and `/v1/info` endpoints are open.

A client is told apart by the user in its token, or by its address when there is no token. The
rate limit is counted in the memory of this one gateway. With several copies it needs a shared
store, which is on the roadmap.

## Run it

```bash
scripts/run.sh ledger-service        # terminal 1
scripts/run.sh api-gateway           # terminal 2
curl http://localhost:8080/ledger/v1/info
```

With Keycloak from `infra/docker-compose.yml`, get a token and use it.

```bash
TOKEN=$(scripts/token.sh alice)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/payments/v1/payments?accountId=<an account id>
```

| Setting | Default | What it is |
|---|---|---|
| `KEYCLOAK_ISSUER` | `http://localhost:8090/realms/bank` | Who signs the tokens |
| `GATEWAY_SECURITY` | `true` | `false` lets every request through. For a laptop with no Keycloak only. A value that is neither stops the gateway at start |
| `LEDGER_URL`, `PAYMENTS_URL`, `NOTIFICATION_URL` and so on | `http://localhost:<port>` | Where each service is |
| `bank.gateway.rate-limit.capacity`, `period` | 120, one minute | The rate limit |
| `bank.gateway.timeout` | 10 seconds | How long to wait for a service |

## Tests

`GatewayTest` starts the real gateway with a small HTTP server behind it and its own signing keys,
so it needs no Keycloak and no Docker. It covers a missing token, an expired token, a token signed by
someone else, the operator role, the rate limit, a service that is down and a service that is slow.

```bash
./mvnw -pl services/api-gateway -am verify
```

## Layout

The gateway holds no business rules, so it has only a `config` package. The routes are built in Java
in `RouteConfiguration`, from the list of services in `application.yml`.
