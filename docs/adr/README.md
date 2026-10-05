# Decisions

Short records of the choices that shape the project, each with its reason and its cost. A new decision
gets a new file. An old one is never edited, only replaced by a later one.

| # | Decision |
|---|---|
| [0001](0001-one-repository-maven-multi-module.md) | One repository and one Maven build |
| [0002](0002-hexagonal-architecture.md) | Hexagonal architecture in every service |
| [0003](0003-money-as-exact-decimal.md) | Money as an exact decimal |
| [0004](0004-kubernetes-for-discovery-and-config.md) | Kubernetes for discovery and configuration, no Eureka or Config Server |
| [0005](0005-spring-boot-4-and-java-21.md) | Spring Boot 4 on Java 21 |
| [0006](0006-pessimistic-locks-in-the-ledger.md) | Row locks in id order when posting to the ledger |
| [0007](0007-transactional-outbox-with-a-polling-relay.md) | Events leave through an outbox table and a polling relay |
| [0008](0008-orchestrated-payment-saga.md) | An orchestrated saga, with its state in the payment row |
| [0009](0009-iso-20022-written-by-hand.md) | ISO 20022 messages written and read with the JDK |
| [0010](0010-responses-checked-against-the-contract.md) | Every response in the integration tests is checked against the contract |
| [0011](0011-opentelemetry-for-all-three-signals.md) | OpenTelemetry for traces, metrics and logs |
