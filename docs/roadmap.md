# Roadmap

## Done so far

- [x] Research into the stacks finance firms in New York use, across eight lines of business
- [x] A plan covering architecture, API design, patterns, security and cloud
- [x] One Maven build with 3 libraries and 11 services
- [x] `Money`, the event envelope, the idempotency key and the error format, with tests
- [x] The first ledger rule with tests, a journal entry whose debits must equal its credits
- [x] Gateway routes for every service
- [x] Draft contracts for ledger, payments, trading, events and market data
- [x] Docker Compose file for the local tools
- [x] CI on GitHub Actions, plus a Jenkinsfile
- [x] Helm chart and Terraform stubs

## Phase 1. Core banking

- [ ] Ledger with accounts, holds, journal posting and balances on PostgreSQL with Flyway
- [ ] Transactional outbox to Kafka
- [ ] Payments saga with book, ACH and instant rails, mapped to ISO 20022
- [ ] Idempotency store
- [ ] Notification consumer that skips duplicates
- [ ] Gateway with Keycloak tokens, rate limits, timeouts and circuit breakers
- [ ] Traces, metrics and logs in Grafana
- [ ] Integration tests with Testcontainers, contract tests, architecture tests

## Phase 2. Compliance and markets

- [ ] KYC, sanctions screening and AML rules with analyst cases
- [ ] Simulated market data over gRPC, ticks in QuestDB
- [ ] Order management and a matching engine on the Disruptor
- [ ] Pre-trade risk checks
- [ ] FIX 4.4 gateway on QuickFIX/J

## Phase 3. Positions, post-trade and wealth

- [ ] Positions and profit and loss from fills
- [ ] Value at Risk and option Greeks
- [ ] Allocations, T+1 settlement and reconciliation against the ledger
- [ ] Model portfolios, rebalancing and time-weighted returns
- [ ] Trade surveillance alerts

## Phase 4. Cloud and operations

- [ ] Images built and scanned in CI
- [ ] Kubernetes with Helm and Argo CD
- [ ] AWS environment with Terraform
- [ ] Load tests with published numbers
- [ ] Operations agent with read-only tools
- [ ] A thin web front end
