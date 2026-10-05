# 0006. Row locks in id order when posting to the ledger

Status. Accepted, October 2026.

## Context

Two payments from one account can arrive at the same moment. If both read the balance before either
writes, both can spend the same money. The first plan was an optimistic lock, a version number on
each account that makes the second writer fail and try again. Under a burst of payments from one
account that meant many failed attempts and a test that passed only most of the time.

## Decision

Posting takes a row lock (`select ... for update`) on every account the entry touches, always in the
order of their ids, with a 5 second wait. The version number stays as a second guard, and a check
constraint in the database refuses a negative customer balance as a third.

## What follows

Payments from one account queue up and each one sees the true balance. The fixed order means two
transfers between the same pair of accounts cannot deadlock. The cost is that a very busy account is
a queue, so its throughput is limited by how fast one entry commits. A bank's own settlement accounts
are the busy ones, and a later phase can spread them over several rows if load tests show the need.
