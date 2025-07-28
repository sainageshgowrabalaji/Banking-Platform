# 🏦 Banking Microservices Platform

This is a robust, industry-grade microservices-based banking platform built with **Java 17**, **Spring Boot 3**, and a modern cloud-native architecture.

## 🚀 Tech Stack

| Category           | Tools/Technologies                                                                 |
|--------------------|-------------------------------------------------------------------------------------|
| Language           | Java 17                                                                            |
| Framework          | Spring Boot, Spring Cloud, Spring Security                                         |
| Microservices Comm | REST, Kafka (event-driven)                                                         |
| Config & Discovery | Spring Cloud Config Server, Netflix Eureka                                        |
| API Gateway        | Spring Cloud Gateway                                                              |
| Messaging          | Apache Kafka                                                                      |
| Persistence        | PostgreSQL, Redis                                                                 |
| Monitoring         | Prometheus, Grafana                                                               |
| CI/CD              | Jenkins, Docker, Kubernetes (K8s)                                                 |
| Code Quality       | SonarQube                                                                         |
| Optional           | Spring AI, OpenTelemetry                                                          |

## 📦 Modules

- `authservice`: Authentication & JWT Token Service
- `accountservice`: User accounts and balances
- `transactionservice`: Fund transfers, deposits, withdrawals
- `notificationservice`: Email/SMS notifications via Kafka
- `analyticsservice`: Event-driven analytics and audit
- `configserver`: Centralized configuration
- `eurekaserver`: Service discovery
- `apigateway`: Unified API entry point with token forwarding
- `shared-libs`: Shared DTOs, utilities, and Feign interfaces
- `infra/`: Docker, K8s, Jenkins, Sonar, Monitoring

## 🏁 Getting Started

```bash
# Start local dev environment with Docker
docker-compose up --build

# Build all services with Maven
./mvnw clean install

# Deploy to Kubernetes (example)
kubectl apply -f infra/k8s/
```

## 🧭 Roadmap

- [x] Setup microservices structure
- [x] Add config, discovery, gateway
- [x] Secure auth with Spring Security + JWT
- [ ] Implement inter-service Kafka messaging
- [ ] Add dashboards, alerts, CI/CD, monitoring
- [ ] Optional cloud deploy to AWS/GCP

## 🤝 Contributing

Contributions are welcome. Please fork and submit PRs!

---
