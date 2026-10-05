# Roadmap

## Done so far

- [x] Research into the stacks finance firms in New York use, across eight lines of business
- [x] A plan covering architecture, API design, patterns, security and cloud
- [x] One Maven build, now with 7 libraries and 11 services
- [x] `Money`, the event envelope, the idempotency key and the error format, with tests
- [x] The first ledger rule with tests, a journal entry whose debits must equal its credits
- [x] Gateway routes for every service
- [x] Draft contracts for ledger, payments, trading, events and market data
- [x] Docker Compose file for the local tools
- [x] CI on GitHub Actions, plus a Jenkinsfile
- [x] Helm chart and Terraform stubs

## Phase 1. Core banking

Built in October 2026.

- [x] Ledger with accounts, holds, journal posting and balances on PostgreSQL with Flyway
- [x] Transactional outbox to Kafka
- [x] Payments saga with book, ACH and instant rails, mapped to ISO 20022
- [x] Idempotency store
- [x] Notification consumer that skips duplicates
- [x] Gateway with Keycloak tokens, rate limits, timeouts and circuit breakers
- [x] Traces, metrics and logs in Grafana
- [x] Integration tests with Testcontainers, contract tests, architecture tests

### What phase 1 leaves for later

These are honest gaps, not bugs. Each one is small on its own and is listed so nobody has to find it
by surprise.

- The rails are simulated. No real ACH file is written and no real network is called
- Screening is a short list of blocked names inside the payments service. The real checks arrive
  with the compliance service in phase 2
- The gateway checks the token. The services behind it trust the gateway and do not check who owns
  an account, so any signed-in user can read any account. Ownership checks need the customer
  records of phase 2
- New accounts are ACTIVE at once. The PENDING status is kept for the identity checks of phase 2
- The rate limit is counted in the memory of one gateway. With several gateway copies it needs a
  shared store such as Redis
- A payment the saga finishes in the background starts a new trace. Only the work done while the
  client waits is in the client's trace
- A parked payment is found with a metric and repaired with a SQL statement. There is no screen or
  endpoint for it yet
- Each service connects to its database as the user that owns the tables. That user could switch
  the ledger's triggers off. A real setup runs the service as a user that cannot
- The outbox relay keeps a database transaction open while it waits for Kafka. It is short and
  bounded, and a later version can send first and mark afterwards
- Notifications are stored and logged. No email or text message is sent
- There is no Grafana dashboard in the repository yet. The data is all there to build one

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
