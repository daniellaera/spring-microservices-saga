# Architecture

## System Overview

```
   ┌──────────────────────────────────────────┐
   │                 Clients                   │
   │   Browser / curl · Frontend UI :8090      │
   │           Thymeleaf + HTMX                │
   └──────────────────────────────────────────┘
                       │ JWT
                       ▼
   ┌──────────────────────────────────────────────────────┐
   │              Spring Cloud Gateway :8080               │
   │   JWT Validation · Routing · Circuit Breaker          │
   │   Rate Limiting (Redis token bucket)                  │
   └──────┬──────────┬────────────┬────────────────────────┘
          │          │            │
          ▼          ▼            ▼
   ┌────────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐
   │Auth :8084  │ │Order   │ │Inventory │ │Payment  │
   │authdb      │ │:8081   │ │:8082     │ │:8083    │
   │            │ │orderdb │ │invntrydb │ │paymentdb│
   └────────────┘ └───┬────┘ └────┬─────┘ └────┬────┘
                      │           │  ↕Redis     │
                      └───────────┴─────────────┘
                                  │ Kafka Topics
                  ┌───────────────▼──────────────────┐
                  │          Apache Kafka :9092        │
                  │  orders-topic · inventory-topic    │
                  │  payment-topic                     │
                  └──────┬────────────────────────────┘
                         │
               ┌─────────▼──────────┐
               │  Notification :8085 │
               └────────────────────┘

   ┌──────────────────────────────────────────────────────┐
   │                    INFRASTRUCTURE                     │
   │                                                       │
   │  ┌──────────────┐  ┌──────────────┐  ┌───────────┐  │
   │  │Config Server │  │Grafana LGTM  │  │Redis :6379│  │
   │  │    :8888     │  │    :3000     │  │Rate limit │  │
   │  │ Spring Cloud │  │Tempo · Loki  │  │+ cache    │  │
   │  │   Config     │  │   · Mimir    │  │           │  │
   │  └──────────────┘  └──────────────┘  └───────────┘  │
   └──────────────────────────────────────────────────────┘
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
              └── inventory-service  → checks stock → updates quantity
                    └── [inventory-topic]
                          └── payment-service  → processes payment
                                └── [payment-topic]
                                      ├── order-service      → CONFIRMED / FAILED
                                      └── notification-service → logs confirmation
```

## Circuit Breaker

```
Gateway → Resilience4j Circuit Breaker per route
  ├── order-cb      → fallback: /fallback/orders      → 503
  ├── inventory-cb  → fallback: /fallback/products    → 503
  └── payment-cb    → fallback: /fallback/transactions → 503

States:
  CLOSED → normal operation, counts failures
  OPEN   → all requests rejected immediately (after >50% failures on 10 calls)
  HALF_OPEN → tests 3 calls after 10s wait → CLOSED or back to OPEN

Metrics: GET http://localhost:8080/actuator/circuitbreakers
```

## Rate Limiting

```
Gateway → Redis RequestRateLimiter (token bucket algorithm)
  ├── replenishRate:  10 req/s  (sustained rate)
  ├── burstCapacity:  20        (peak allowed)
  └── requestedTokens: 1        (cost per request)

KeyResolver:
  ├── With JWT token  → rate limit per user (substring of token)
  └── Without token   → rate limit per IP address

Response when exceeded: 429 Too Many Requests
```

## Redis Cache (Inventory Service)

```
GET /products
  ├── Cache HIT  → return from Redis (no DB query)
  └── Cache MISS → query DB → store in Redis → return
  
GET /products/{id}
  └── no cache — direct DB query
  └── 404 if not found → GlobalExceptionHandler → ErrorResponse

POST /products
  └── Create product → @CacheEvict → invalidate "products" cache

Redis key: "products::SimpleKey []"
TTL: none (evicted on write)
```

## Centralized Configuration

```
config-server (8888)
  └── configs/
       ├── application.yml        ← shared: OpenTelemetry, tracing, logging
       ├── application-dev.yml    ← shared dev: DEBUG logging
       ├── auth-service.yml
       ├── order-service.yml
       ├── inventory-service.yml
       ├── payment-service.yml
       ├── gateway-service.yml
       ├── notification-service.yml
       └── frontend-service.yml

Profile activation:
  dev  → spring.profiles.active=dev  (local IntelliJ)
  prod → no profile (Docker compose, default application.yml)
```

## Observability Stack

```
Each service
  └── spring-boot-starter-opentelemetry
        ├── Traces  → OTLP → Grafana Tempo  (distributed request tracing)
        ├── Metrics → OTLP → Grafana Mimir  (service metrics)
        └── Logs    → OTLP → Grafana Loki   (centralized logging)

Every log line includes traceId:
[order-service] [nio-8081-exec-1] [f9b4100b3004d2e68a306bf2862c67f1] ...
```

## Infrastructure (infra-node1 — Production)

```
Internet → Nginx (:80) → Gateway (:8080)
                              ├── Auth (:8084)
                              ├── Order (:8081)
                              ├── Inventory (:8082)
                              ├── Payment (:8083)
                              ├── Notification (:8085)
                              └── Frontend (:8090)
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
Kafka decouples services in time — if payment-service is down, the message stays in the topic and is consumed when it comes back. With REST, a failed call means a lost event. Kafka also enables multiple consumers on the same topic (order-service + notification-service both consume payment-topic).

### Why Spring Cloud Config?
Centralizes configuration across all services. One change in `configs/application.yml` propagates to all services on restart. Dev/prod profiles managed centrally — no need to touch individual service YMLs.

### Why a dedicated auth-service instead of auth in the gateway?
The gateway is WebFlux (reactive). Putting user management (DB access, BCrypt) in the gateway would mix concerns. The auth-service is standard WebMVC with JPA — simpler and independently deployable.

### Why Resilience4j Circuit Breaker only on the gateway?
Services communicate via Kafka (async) — Circuit Breaker is most valuable on synchronous HTTP calls. The gateway is the single entry point for all HTTP traffic, making it the right place for resilience patterns. Adding Circuit Breaker inside each service too would create double-retry issues.

### Why Redis for rate limiting AND caching?
Redis is already required by Spring Cloud Gateway's `RequestRateLimiter`. Adding product caching in inventory-service reuses the same infrastructure with zero added complexity. One Redis instance, two use cases.

### Why Thymeleaf + HTMX for the frontend?
No build toolchain, no heavy framework, no separate deployment. HTMX enables dynamic interactions via HTML attributes. The frontend is a simple Spring Boot service that calls the gateway — consistent with the rest of the architecture.

### Why Grafana LGTM?
Single Docker image bundling Loki + Grafana + Tempo + Mimir. Zero configuration needed for a full observability stack in dev. In prod, each component could be deployed separately for scalability.