# Architecture

## System Overview

```
   ┌──────────────────────────────────────┐
   │               Clients                 │
   │   Browser / curl · Frontend UI :8090  │
   │         Thymeleaf + HTMX              │
   └──────────────────────────────────────┘
                      │ JWT
                      ▼
   ┌─────────────────────────────────────────────┐
   │          Spring Cloud Gateway :8080          │
   │   JWT Validation · Routing · Circuit Breaker │
   └──────┬──────────┬────────────┬──────────────┘
          │          │            │
          ▼          ▼            ▼
   ┌────────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐
   │Auth :8084  │ │Order   │ │Inventory │ │Payment  │
   │authdb      │ │:8081   │ │:8082     │ │:8083    │
   │            │ │orderdb │ │invntrydb │ │paymentdb│
   └────────────┘ └───┬────┘ └────┬─────┘ └────┬────┘
                      │           │             │
                      └───────────┴─────────────┘
                                  │ Kafka Topics
                  ┌───────────────▼──────────────────┐
                  │          Apache Kafka :9092        │
                  │  orders-topic · inventory-topic    │
                  │  payment-topic                     │
                  └───────────────┬──────────────────┘
                                  │
                          ┌───────▼────────┐
                          │  Notification  │
                          │    :8085       │
                          └───────────────┘

   ┌──────────────────────────────────────────────────┐
   │                 INFRASTRUCTURE                    │
   │                                                   │
   │  ┌──────────────┐    ┌──────────────────────┐    │
   │  │Config Server │    │   Grafana LGTM :3000  │    │
   │  │    :8888     │    │  Tempo · Loki · Mimir │    │
   │  │ Spring Cloud │    │   OpenTelemetry OTLP  │    │
   │  │   Config     │    │      :4317 · :4318    │    │
   │  └──────────────┘    └──────────────────────┘    │
   └──────────────────────────────────────────────────┘
```

## Authentication Flow

```
POST /auth/register → auth-service → BCrypt hash → save user → return JWT
POST /auth/login    → auth-service → validate credentials → return JWT
GET  /orders        → gateway validates JWT → forward to order-service
GET  /orders        → gateway (no token) → 401 Unauthorized
```

## Saga Flow (Event-Driven)

```
POST /orders
  └── order-service        → saves order (PENDING)
        └── [orders-topic]
              └── inventory-service  → checks stock
                    └── [inventory-topic]
                          └── payment-service  → processes payment
                                └── [payment-topic]
                                      ├── order-service      → updates status (CONFIRMED / FAILED)
                                      └── notification-service → logs confirmation
```

## Circuit Breaker

```
Gateway → Resilience4j Circuit Breaker per route
  ├── order-cb      → fallback: /fallback/orders
  ├── inventory-cb  → fallback: /fallback/products
  └── payment-cb    → fallback: /fallback/transactions

States: CLOSED → OPEN (>50% failures) → HALF_OPEN (after 10s) → CLOSED
```

## Centralized Configuration

```
config-server (8888)
  └── configs/
       ├── application.yml        ← shared: OpenTelemetry, tracing (all services)
       ├── application-dev.yml    ← shared dev: DEBUG logging
       ├── auth-service.yml
       ├── order-service.yml
       ├── inventory-service.yml
       ├── payment-service.yml
       ├── gateway-service.yml
       ├── notification-service.yml
       └── frontend-service.yml

Each service at startup:
  spring.config.import=configserver:http://config-server:8888
```

## Observability Stack

```
Each service
  └── spring-boot-starter-opentelemetry
        ├── Traces  → OTLP → Grafana Tempo
        ├── Metrics → OTLP → Grafana Mimir
        └── Logs    → OTLP → Grafana Loki

Every log line includes traceId:
[order-service] [nio-8081-exec-1] [f9b4100b3004d2e68a306bf2862c67f1-7b3daefca6eeca53] ...
```

## Infrastructure (infra-node1)

```
Internet → Nginx (:80) → Gateway (:8080) → Services
                                          → Auth (:8084)
                                          → Order (:8081)
                                          → Inventory (:8082)
                                          → Payment (:8083)
                                          → Notification (:8085)
                                          → Frontend (:8090)
```

## Database per Service

| Service | Database | Migrations |
|---------|----------|------------|
| auth-service | authdb | Flyway |
| order-service | orderdb | Flyway |
| inventory-service | inventorydb | Flyway |
| payment-service | paymentdb | Flyway |

## Tech Decisions

### Why Kafka over REST between services?
Kafka decouples services in time — if payment-service is down, the message stays in the topic and is consumed when it comes back. With REST, a failed call means a lost event.

### Why Spring Cloud Config?
Centralizes configuration across all services. One change in `configs/application.yml` propagates to all services on restart — no need to touch multiple separate yml files. Dev/prod profiles are managed centrally.

### Why a dedicated auth-service instead of auth in the gateway?
The gateway is WebFlux (reactive). Putting user management (DB access, BCrypt) in the gateway would mix concerns. The auth-service is a standard WebMVC service with JPA — simpler and more maintainable.

### Why Resilience4j Circuit Breaker only on the gateway?
Services communicate via Kafka (async) — Circuit Breaker is most valuable on synchronous HTTP calls. The gateway is the single entry point for all HTTP traffic, making it the right place for resilience patterns.

### Why Thymeleaf + HTMX for the frontend?
No build toolchain, no heavy framework. HTMX enables dynamic interactions via HTML attributes — the frontend is a simple Spring Boot service that calls the gateway, keeping the architecture consistent and lightweight.

### Why Grafana LGTM?
Single Docker image that bundles Loki + Grafana + Tempo + Mimir. Zero config needed for a full observability stack in dev.