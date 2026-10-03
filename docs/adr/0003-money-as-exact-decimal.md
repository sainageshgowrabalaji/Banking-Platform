# 0003. Money as an exact decimal

Status. Accepted, October 2026.

## Context

Floating point numbers cannot hold most decimal fractions exactly. A ledger built on them drifts by
fractions of a cent and stops balancing.

## Decision

Money in Java is the `Money` record, a `BigDecimal` at the scale of its currency together with the
currency. In the database it is a whole number of minor units (cents) with a currency code. In JSON it
is a decimal string, because JSON numbers are floating point in many clients.

Prices on the trading side are whole numbers of ten-thousandths, which is how exchanges send them.

## What follows

Arithmetic is exact and two currencies can never be mixed by accident. The cost is that `Money` is more
verbose than a number, and every API must parse and validate amounts.
