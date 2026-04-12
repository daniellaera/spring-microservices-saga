# Architecture & Roadmap

## System Architecture

```
                          ┌─────────────────────────────────────────────┐
                          │              ONLINE SHOP PLATFORM             │
                          └─────────────────────────────────────────────┘

   ┌──────────┐     JWT    ┌─────────────────────────────────────────────┐
   │  Client  │ ─────────▶ │          Spring Cloud Gateway :8080          │
   │ (Browser │ ◀───────── │     JWT Validation · Routing · Security      │
   │  /curl)  │            └──────┬──────────┬────────────┬──────────────┘
   └──────────┘                   │          │            │
                                  │          │            │
              ┌───────────────────┼──────────┼────────────┼──────────────┐
              │                   ▼          ▼            ▼              │
              │  ┌─────────────┐ ┌─────────┐ ┌─────────┐ ┌───────────┐  │
              │  │Auth Service │ │ Order   │ │Inventory│ │ Payment   │  │
              │  │   :8084     │ │ :8081   │ │  :8082  │ │  :8083    │  │
              │  │  authdb     │ │ orderdb │ │invntrydb│ │ paymentdb │  │
              │  └─────────────┘ └────┬────┘ └────┬────┘ └─────┬─────┘  │
              │                       │            │            │        │
              │         SERVICES      └────────────┴────────────┘        │
              └───────────────────────────────────────────────────────────┘
                                       │    Kafka Topics
                          ┌────────────▼────────────────────────┐
                          │            Apache Kafka :9092         │
                          │  orders-topic · inventory-topic       │
                          │  payment-topic                        │
                          └─────────────────────────────────────┘

              ┌─────────────────────────────────────────────────┐
              │              INFRASTRUCTURE                       │
              │                                                   │
              │  ┌──────────────┐    ┌──────────────────────┐   │
              │  │Config Server │    │   Grafana LGTM :3000  │   │
              │  │    :8888     │    │  Tempo · Loki · Mimir │   │
              │  │ Spring Cloud │    │   OpenTelemetry OTLP  │   │
              │  │   Config     │    │      :4317 · :4318    │   │
              │  └──────────────┘    └──────────────────────┘   │
              └─────────────────────────────────────────────────┘

## Saga Flow

```
1. Client POSTs /orders (with JWT)
2. Gateway validates JWT → routes to order-service
3. order-service saves order (PENDING) → publishes to [orders-topic]
4. inventory-service consumes → checks stock → publishes to [inventory-topic]
5. payment-service consumes → processes payment → publishes to [payment-topic]
6. order-service consumes → updates status → CONFIRMED or FAILED
```

## Roadmap

### 🚧 In Progress
- [ ] **Notification Service** — Kafka consumer on payment-topic, sends email on order confirmation

### 📋 Planned
- [ ] **Circuit Breaker** — Resilience4j on inter-service calls, fallback responses
- [ ] **Rate Limiting** — Spring Cloud Gateway rate limiter per user/IP
- [ ] **Unit & Integration Tests** — JUnit 5, Mockito, Testcontainers for all services
- [ ] **OpenRewrite** — Automated migration recipes for Java/Spring upgrades

### ✅ Done
- [x] Event-driven Saga pattern (Kafka)
- [x] JWT Authentication (auth-service + gateway validation)
- [x] Spring Cloud Config Server (centralized configuration)
- [x] Distributed tracing (OpenTelemetry + Grafana Tempo)
- [x] API Gateway (Spring Cloud Gateway)
- [x] Database per service (PostgreSQL + Flyway)
- [x] Docker Compose (local + prod)
- [x] Multi-arch Docker images (amd64 + arm64)
- [x] Production deployment (infra-node1 + Nginx)
