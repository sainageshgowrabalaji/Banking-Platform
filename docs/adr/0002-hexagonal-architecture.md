# 0002. Hexagonal architecture in every service

Status. Accepted, October 2026.

## Context

In a typical layered Spring service the business rules end up mixed with controllers, JPA entities and
framework annotations. They become slow to test and risky to change.

## Decision

Every service has the same packages. `domain` holds rules in plain Java. `application` holds use cases
and the ports they need. `adapter.in` and `adapter.out` connect the ports to HTTP, the database and
Kafka. Dependencies only point inward.

## What follows

Rules are tested without Spring, in milliseconds. Anyone who knows one service can find their way
around all of them. The cost is some mapping code between the domain and the adapters. An architecture
test will enforce the rule in phase 1.
