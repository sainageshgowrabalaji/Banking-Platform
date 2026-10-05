# 0010. Every response in the integration tests is checked against the contract

Status. Accepted, October 2026.

## Context

The APIs are written first, as OpenAPI files that other teams build against. Code and contract drift
apart quietly. A field is renamed, or a new error status appears, and the file still says the old
thing.

## Decision

The HTTP client the integration tests use compares every response with the OpenAPI file, and every
request that the service accepted. The comparison is strict. A field that is not in the file fails
the test, and so does a status code or a path the file does not list. The same contract files are
also checked for validity in CI.

## What follows

A change to an API has to start in the contract, or the build fails. The check costs nothing extra
to write, because it rides on the tests that already call the endpoints. It only covers what those
tests call, so an endpoint with no test has no check. It also checks one side. A consumer-driven tool
such as Pact, where each client records what it expects, would cover the other, and is worth adding
when a second team's client exists.
