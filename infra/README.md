# Local infrastructure

One Docker Compose file starts every tool the services need on a laptop.

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

| Tool | Why it is here | Address |
|---|---|---|
| PostgreSQL 17 | One database per service | `localhost:5432` |
| Kafka 4 (KRaft) | Events between services | `localhost:9092` |
| Keycloak | Login, tokens and roles | http://localhost:8090 |
| Grafana, Tempo, Loki, Prometheus | Traces, logs and metrics | http://localhost:3000 |
| Redis 7 | Not used by phase 1. Kept for sharing rate limits between gateway copies | `localhost:6379` |
| QuestDB (markets profile) | Tick history | http://localhost:9000 |
| MinIO (markets profile) | Statements and audit exports | http://localhost:9101 |

The ledger, payments and notification services need PostgreSQL and Kafka to start. The gateway needs
Keycloak to check tokens, unless it is started with `GATEWAY_SECURITY=false`. Grafana is optional.

The unit tests need none of this. The integration tests start their own PostgreSQL and Kafka
containers and do not use this file.

## Keycloak

On its first start Keycloak loads the `bank` realm from `keycloak/bank-realm.json`. It has two roles
and two users, for a laptop only.

| User | Password | Roles | Can do |
|---|---|---|---|
| `alice` | `alice_local_only` | `bank-customer` | Open accounts, send payments, read balances |
| `olivia` | `olivia_local_only` | `bank-customer`, `bank-operator` | Also post journal entries, place holds and freeze accounts |

```bash
scripts/token.sh olivia          # prints an access token
```

The realm file is read only when the realm does not exist yet. To load a changed file, remove the
Keycloak container with `docker compose -f infra/docker-compose.yml rm -sf keycloak` and start it again.

## Grafana

Start the services with `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` and open
http://localhost:3000. Under Explore, Tempo has the traces, Loki has the logs and Prometheus has the
metrics. Every log record carries the trace id of its request, so the lines of one trace can be found
by searching Loki for that id.

## Image tags

The tags for Grafana, QuestDB and MinIO are `latest` for now. Pin them to exact versions after the
first pull, so every machine runs the same thing.
