# 📐 Banking Platform Architecture Overview

This document provides a high-level architecture overview of the modular banking platform.

## 🏗️ Architecture

```
                 +-------------+
                 | API Gateway |
                 +------+------++
                        |      
      +-----------------+-------------------+
      |                 |                   |
+-----v-----+     +-----v-----+       +-----v-----+
| AuthSvc   |     | AccountSvc|       | TransactionSvc|
+-----------+     +-----------+       +---------------+
      |                |                     |
      +--------+-------+----------+----------+
               |                  |
         +-----v-----+      +-----v-----+
         | Kafka Bus |      | Redis     |
         +-----------+      +-----------+

          +------------+
          | Postgres DB|
          +------------+

          +------------------+
          | Eureka & Config  |
          +------------------+

          +------------------+
          | Prometheus/Grafana |
          +------------------+
```

## 🧩 Services & Responsibility

- **Auth Service:** Login, Register, JWT token handling
- **Account Service:** Account info, balance, linked users
- **Transaction Service:** Transfers, deposits, withdrawals
- **Notification Service:** Asynchronous Kafka-based notifier
- **Analytics Service:** Reads events from Kafka for analysis
- **Config Server:** Central config management via Git
- **Eureka Server:** Service registry and discovery
- **API Gateway:** External access, routing, token forwarding

## 🛠️ Inter-Service Communication

- **Synchronous:** REST via Feign Clients (Service-to-Service)
- **Asynchronous:** Kafka-based event publishing/subscription

## 🔐 Security

- Spring Security + JWT
- Token-based access from Gateway to downstream services

---
