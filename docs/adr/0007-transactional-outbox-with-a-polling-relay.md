# 0007. Events leave through an outbox table and a polling relay

Status. Accepted, October 2026.

## Context

A service that changes its database and then calls Kafka can stop between the two. Then the money
moved and nobody was told, or an event went out for a change that was rolled back. Both are
unacceptable for a ledger.

## Decision

Business code never calls Kafka. It writes the event into an `outbox_event` table in the same
transaction as the business change. A relay in the same service reads the table every half second
and sends the rows to Kafka in order. A PostgreSQL advisory lock makes sure only one copy of the
service relays at a time. Consumers store the ids they have handled and skip a repeat.

## What follows

An event is never lost and never sent for a change that did not happen. Delivery is at least once,
so every consumer must be safe against duplicates, which `ProcessedEvents` gives them. Events arrive
up to half a second late. The other common design reads the database's change log with Debezium. It
is faster and adds no polling, but it brings Kafka Connect to run and watch, which is too much
machinery for this stage.
