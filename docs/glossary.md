# Glossary

The words of the business, in plain language. Knowing these is half of a finance interview.

## Banking and ledger

| Term | Meaning |
|---|---|
| Ledger | The record of every movement of money. The source of truth for balances |
| Journal entry | One balanced movement, made of two or more postings |
| Posting | One line of an entry. A debit or a credit on one account |
| Debit and credit | The two sides of an entry. For a customer deposit account, a credit adds money and a debit removes it |
| Hold | Money reserved for a payment that has not settled yet |
| Ledger balance | The sum of posted entries |
| Available balance | The ledger balance minus holds. What the customer can spend |
| Reconciliation | Comparing two records of the same thing to find differences |

## Payments

| Term | Meaning |
|---|---|
| Rail | The network a payment travels on |
| ACH | The US batch network. Cheap, settles in hours or the next day |
| Wire | A single high-value payment, settled one by one (Fedwire, CHIPS) |
| Instant payment | Settles in seconds, any time of day (RTP, FedNow) |
| Book transfer | A payment between two accounts at the same bank. No network needed |
| ISO 20022 | The global message standard for payments |
| pain.001 | A customer asks its bank to pay someone |
| pacs.008 | One bank sends a customer payment to another bank |
| pacs.002 | A status report on a payment |
| camt.053 | An end-of-day account statement |
| Cut-off | The time after which a payment waits for the next business day |
| Settlement account | The bank's own account that money leaving on a rail is credited to, until the network settles |
| Return | An ACH payment sent back by the receiving bank, for example because the account is closed |
| Status code | Where a payment is. RCVD received, ACCP accepted, ACSP in process, ACSC settled, RJCT rejected, CANC cancelled |
| Reason code | Why a payment was rejected. AM04 not enough money, AC04 closed account, AC01 wrong account number |

## Platform

| Term | Meaning |
|---|---|
| Idempotency key | A value the client sends with a write, so a retry returns the first result and changes nothing twice |
| Saga | A chain of local steps across services, each with an undo, used where no single transaction can cover the work |
| Outbox | A table where a service saves its events in the same transaction as its data, for a relay to send to Kafka |
| Dead letter topic | Where a consumer parks an event it could not handle, so the rest keep flowing |
| Circuit breaker | A switch that stops calls to a failing service for a while, so callers fail fast |
| Token bucket | A rate limit that refills at a steady pace and allows short bursts |
| Trace | The path of one request through every service it touched |
| Contract | The written API that other teams build against |

## Compliance

| Term | Meaning |
|---|---|
| KYC | Know Your Customer. Checking who a customer is before opening an account |
| AML | Anti-Money Laundering. Watching transactions for suspicious patterns |
| Sanctions screening | Checking names against government lists such as OFAC |
| SAR | Suspicious Activity Report, filed with the regulator |
| Surveillance | Watching orders and trades for market abuse, such as spoofing |

## Trading

| Term | Meaning |
|---|---|
| Order | An instruction to buy or sell |
| Limit order | Buy or sell at this price or better |
| Market order | Buy or sell now at the best price available |
| Order book | All open orders for one instrument, sorted by price |
| Bid and ask | The best price a buyer offers, and the best price a seller accepts |
| Spread | The gap between the bid and the ask |
| Matching engine | The program that pairs buy and sell orders |
| Price-time priority | The best price trades first. At the same price, the earliest order trades first |
| Fill or execution | A trade that happened, for all or part of an order |
| FIX | The message protocol trading systems use to talk to each other |
| OMS | Order Management System. Tracks orders through their life |
| Pre-trade risk | Checks that run before an order reaches the market |

## Positions and risk

| Term | Meaning |
|---|---|
| Position | How much of an instrument an account holds |
| Realised P&L | Profit or loss locked in by closing a position |
| Unrealised P&L | Profit or loss on what is still held, at the current price |
| Mark to market | Valuing a position at the current market price |
| VaR | Value at Risk. A loss that should be exceeded only rarely, such as on 1 day in 100 |
| Greeks | How an option's price reacts to changes in price, time and volatility |

## Post-trade

| Term | Meaning |
|---|---|
| Allocation | Splitting one large trade across the accounts it was made for |
| Clearing | Working out who owes what after trading |
| Settlement | The actual exchange of cash and securities |
| T+1 | Settlement one business day after the trade. The US standard since May 2024 |
| Break | A mismatch found in reconciliation |
| CAT | Consolidated Audit Trail. US reporting of every order event |

## Wealth

| Term | Meaning |
|---|---|
| Model portfolio | A target mix of holdings, such as 60 percent stocks and 40 percent bonds |
| Drift | How far a real portfolio has moved from its model |
| Rebalancing | Trading to bring a portfolio back to its model |
| Time-weighted return | Performance that ignores the timing of deposits and withdrawals |
