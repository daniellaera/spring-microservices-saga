# Online Shop — Microservices Platform

A production-grade microservices e-commerce platform built on Java 21 and Spring Boot 4, demonstrating enterprise patterns including event-driven saga choreography, transactional outbox, real-time SSE updates, JWT gateway authentication, and Stripe payment processing. Deployed on a self-hosted Proxmox homelab with Gitea Actions CI/CD and Infisical secrets management.

---

## Architecture

```
┌─────────────┐     ┌──────────────────────────────────────┐
│   shop-ui   │────▶│           gateway-service            │
│  Angular 21 │◀────│  Spring Cloud Gateway · JWT · RBAC   │
│ PrimeNG/SSE │     │  Circuit Breaker · Rate Limiter       │
└─────────────┘     └──────┬───────────────────┬────────────┘
                           │                   │
          ┌────────────────┼──────────┐         ▼
          ▼                ▼          ▼    ┌─────────────┐
  ┌──────────────┐  ┌──────────┐  ┌──────────────┐      │  auth-service │
  │order-service │  │inventory │  │   payment    │      │  JWT + BCrypt │
  │  Outbox +    │  │  :8082   │  │   :8083      │      └──────────────┘
  │  SSE :8081   │  │  Redis   │  │   Stripe     │
  └──────┬───────┘  │  cache   │  │   SDK        │
         │          └──────────┘  └──────┬───────┘
         │                               │
  ┌──────▼───────────────────────────────▼──────────────────┐
  │                  Apache Kafka 4.2 (KRaft)                │
  │       orders-topic → inventory-topic → payment-topic     │
  └──────────────────────────┬──────────────────────────────┘
                             │
              ┌──────────────▼──────────────┐
              │     notification-service     │
              │     :8085 · Gmail SMTP       │
              └─────────────────────────────┘

  ┌─────────────────────────────────────────────────────────┐
  │                     INFRASTRUCTURE                       │
  │  config-server :8888   Grafana LGTM :3000   Redis :6379 │
  │  Spring Cloud Config   Tempo·Loki·Mimir     rate limit  │
  │                                             + cache      │
  └─────────────────────────────────────────────────────────┘
```

---

## Tech Stack

### Backend
| Technology | Version | Usage |
|------------|---------|-------|
| Java | 21 | Virtual threads, records |
| Spring Boot | 4.0.2 | Core framework |
| Spring Cloud Gateway | 2025.1.1 | API gateway, routing, JWT, RBAC |
| Spring Cloud Config | 2025.1.1 | Centralized configuration |
| Spring Security | 7 | JWT authentication, RBAC |
| Resilience4j | latest | Circuit Breaker (CLOSED / OPEN / HALF_OPEN) |
| Apache Kafka | 4.2.0 | Event-driven saga (KRaft — no Zookeeper) |
| PostgreSQL | 17 | Database per service |
| Redis | 8 | Rate limiting (token bucket) + product cache |
| Flyway | 11 | Database migrations |
| Stripe SDK | latest | Test-mode PaymentIntent API |
| OpenTelemetry | latest | Distributed traces + metrics + logs |
| Grafana LGTM | latest | Tempo + Loki + Mimir in one container |
| Testcontainers | 1.21.4 | Integration tests — real PostgreSQL + Kafka |

### Frontend
| Technology | Version | Usage |
|------------|---------|-------|
| Angular | 21 | Single-page application |
| PrimeNG | 21 | UI components (Aura theme) |
| Stripe Elements | latest | Embedded, PCI-compliant payment UI |

### Infrastructure
| Technology | Usage |
|------------|-------|
| Docker Compose | Local dev + prod profiles |
| Gitea Actions | CI/CD pipeline |
| Infisical (self-hosted) | Secrets management |
| Proxmox | Homelab deployment (infra-node1) |

---

## Services

| Service | Port | Description |
|---------|------|-------------|
| shop-ui | 80 | Angular 21 frontend — PrimeNG Aura UI |
| gateway-service | 8080 | Spring Cloud Gateway — JWT, rate limiting, circuit breaker |
| auth-service | 8084 | User registration, JWT tokens, BCrypt |
| order-service | 8081 | Order management, Outbox pattern, SSE push |
| inventory-service | 8082 | Product catalog, stock management, Redis cache |
| payment-service | 8083 | Stripe PaymentIntent integration, Kafka saga |
| notification-service | 8085 | Order confirmation emails via Gmail SMTP |
| config-server | 8888 | Centralized Spring Cloud Config Server |

---

## Key Architectural Patterns

### Saga Pattern (Choreography)
Order placement triggers a distributed saga across three services via Kafka:
1. `order-service` creates order (PENDING) → publishes to `orders-topic`
2. `inventory-service` validates and reserves stock → publishes to `inventory-topic`
3. `payment-service` verifies Stripe PaymentIntent → publishes to `payment-topic`
4. `order-service` receives result → marks order CONFIRMED or FAILED
5. On failure: automatic compensation — stock restored, order cancelled

### Transactional Outbox
Prevents dual-write between PostgreSQL and Kafka. The order and its `OutboxEvent` are written in a single database transaction. A scheduler polls the outbox table and publishes pending events to Kafka, guaranteeing at-least-once delivery even if the broker is temporarily unavailable.

### Real-time Updates (SSE)
The browser holds one persistent Server-Sent Events connection to `order-service`. When an order changes state (PENDING → CONFIRMED / FAILED), the update is pushed instantly — no polling, no WebSocket handshake overhead.

### JWT Gateway Authentication
All JWT validation happens at the gateway. Downstream services receive `X-User-Email` and `X-User-Role` headers injected by the gateway — they never inspect the token directly. RBAC rules (e.g. `POST /products` requires `ADMIN`) are enforced before a request reaches any microservice.

---

## Getting Started

### Prerequisites
- Java 21
- Node 24 (nvm recommended)
- Docker + Docker Compose
- Maven 3.9+

### Local Development

```bash
# Start infrastructure (Kafka, PostgreSQL, Redis, Grafana, Mailpit)
docker compose -f docker-compose.local.yml up -d

# Start all backend services
./start-services.sh

# Start the frontend (separate terminal)
cd shop-ui && npx ng serve
```

Frontend available at `http://localhost:4200`

### Demo Credentials

| User | Email | Password | Role |
|------|-------|----------|------|
| User | daniel@example.com | demo1234 | USER |
| Admin | admin@example.com | demo1234 | ADMIN |

> Seeded by Flyway on first startup. Do not use in production.

To provision a new ADMIN after deployment:
```bash
./scripts/create-admin.sh admin@shop.com yourPassword
```

### Test Payment (Stripe test mode)

| Card number | Result |
|-------------|--------|
| 4242 4242 4242 4242 | Payment succeeds |
| 4000 0000 0000 0002 | Card declined |
| 4000 0025 0000 3155 | Requires 3D Secure |

Use any future expiry and any 3-digit CVC.

---

## CI/CD Pipeline

Gitea Actions with path-based change detection — only changed services are rebuilt:
- Docker images built for `linux/amd64` + `linux/arm64`, pushed to Docker Hub
- Secrets fetched from self-hosted Infisical via Machine Identity (Universal Auth)
- Services deployed to Proxmox homelab (`infra-node1`) via SSH

---

## Observability

| Signal | Backend | How |
|--------|---------|-----|
| Distributed traces | Grafana Tempo | OpenTelemetry OTLP |
| Metrics | Grafana Mimir | Micrometer OTLP registry |
| Logs | Grafana Loki | OpenTelemetry Logback appender |
| Uptime | Uptime Kuma | `/actuator/health` every 60 s + Telegram alerts |

Every log line carries a `traceId` for cross-service correlation. Access Grafana at `http://localhost:3000`.

---

## API Reference

All requests go through the gateway at port `8080`.

```
POST /auth/register       — register (returns JWT, role=USER)
POST /auth/login          — login   (returns JWT)

GET  /products            — list products (Redis-cached)
POST /products            — create product (ADMIN only)

POST /orders              — place order
GET  /orders              — list orders for authenticated user
GET  /orders/{id}         — get order by ID
GET  /orders/sse          — SSE stream for real-time order updates

GET  /transactions        — list payment transactions
GET  /transactions/{id}   — get transaction by ID
```

### Swagger UI (direct service access)
```
http://localhost:8081/swagger-ui.html  — Order Service
http://localhost:8082/swagger-ui.html  — Inventory Service
http://localhost:8083/swagger-ui.html  — Payment Service
```

---

## Project Structure

```
online-shop/
├── auth-service/
├── config-server/
├── gateway-service/
├── inventory-service/
├── notification-service/
├── order-service/
├── payment-service/
├── shop-ui/
├── docs/
│   ├── ARCHITECTURE.md       ← design, flows, ADRs
│   └── ROADMAP.md
├── scripts/
│   └── create-admin.sh       ← provision first ADMIN post-deployment
├── docker-compose.yml        ← full stack (prod-like)
├── docker-compose.local.yml  ← infrastructure only (dev)
├── start-services.sh
└── pom.xml
```

## Docker Hub

```
daniellaera/config-server:latest
daniellaera/auth-service:latest
daniellaera/gateway-service:latest
daniellaera/order-service:latest
daniellaera/inventory-service:latest
daniellaera/payment-service:latest
daniellaera/notification-service:latest
daniellaera/shop-ui:latest
```

---

See [Architecture](./docs/ARCHITECTURE.md) for detailed flow diagrams and architectural decision records.
