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
| Redis 7 | Rate limits, idempotency keys, caches | `localhost:6379` |
| Keycloak | Login, tokens and roles | http://localhost:8090 |
| Grafana, Tempo, Loki, Prometheus | Traces, logs and metrics | http://localhost:3000 |
| QuestDB (markets profile) | Tick history | http://localhost:9000 |
| MinIO (markets profile) | Statements and audit exports | http://localhost:9101 |

The skeleton services do not connect to any of these yet, so the build and the tests pass with Docker
switched off. Each tool is wired in during the phase that needs it.

The image tags for Grafana, QuestDB and MinIO are `latest` for now. Pin them to exact versions after the
first pull, so every machine runs the same thing.
