# 0008. An orchestrated saga, with its state in the payment row

Status. Accepted, October 2026.

## Context

A payment touches the payments database, the ledger and a rail. No single transaction covers all
three, so the work has to be a chain of steps that can each fail and be undone. The chain can be run
by one orchestrator that calls each step, or by choreography, where each service reacts to the
events of the others.

## Decision

One class, `PaymentSaga`, knows the whole route. The status of the saga is the status of the payment,
saved in the `payment` row after every step. A step claims the row, calls one other system and saves
the result. A background worker picks up any payment that still needs work. Every call to another
system carries the payment's own reference and can be repeated safely.

## What follows

The full route of a payment can be read in one file, and a stuck payment can be found with one SQL
query. A crash or a ledger outage delays a payment and never loses it. The cost is that the payments
service knows about the ledger and the rails, where choreography would keep them apart. No workflow
engine such as Temporal or Camunda is used. Many banks do use one, and it would be the natural next
step if the number of steps grows.
