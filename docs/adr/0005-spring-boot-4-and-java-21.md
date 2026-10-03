# 0005. Spring Boot 4 on Java 21

Status. Accepted, October 2026.

## Context

The first version of this project used Spring Boot 3.4 on Java 17. Spring Boot 4 is the current line.
Java 21 is a long-term support release with virtual threads, records and pattern matching.

## Decision

Use Spring Boot 4.1 with Spring Cloud 2025.1, and compile for Java 21. Virtual threads are switched on
in every service, so a blocking call to a database or another service does not hold an operating system
thread. The build refuses to run on an older JDK and says how to install the right one.

## What follows

The code uses the current APIs that new bank projects are started on. The cost is that many bank
systems still run Boot 3 or older, so some examples online will differ. The differences are small and
are noted in the code where they matter.
