# Online Shop — Microservices Platform

![Coverage](https://img.shields.io/badge/coverage-85%25-green)
![Tests](https://img.shields.io/badge/tests-220%20passing-brightgreen)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)
![Angular](https://img.shields.io/badge/Angular-21-red)

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
| Redis | 8 | Rate limiting, cart sessions, product cache |
| Refresh Token Rotation | — | 15-min access / 7-day refresh, rotated on every use |
| Email Availability Check | — | Real-time email availability check on registration |
| Flyway | 11 | Database migrations |
| Stripe SDK | latest | Test-mode PaymentIntent API |
| OpenTelemetry | latest | Distributed traces + metrics + logs |
| Grafana LGTM | latest | Tempo + Loki + Mimir in one container |
| Testcontainers | 1.21.4 | Integration tests — real PostgreSQL + Kafka |
| JaCoCo | latest | Aggregate coverage report across all services |

### Frontend
| Technology | Version | Usage |
|------------|---------|-------|
| Angular | 21 | Single-page application |
| PrimeNG | 21 | UI components (Aura theme) |
| Stripe Elements | latest | Embedded, PCI-compliant payment UI |
| Dark/Light Mode | — | Theme toggle (PrimeNG Aura) |
| Notification Panel | — | In-app notification panel with event history |

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
| cart-service | 8086 | Redis-backed shopping cart with 30min TTL |
| audit-trail-service | 8087 | Append-only financial audit trail — Kafka consumer, admin REST API |
| config-server | 8888 | Centralized Spring Cloud Config Server |
| dozzle | 9999 | Real-time Docker log viewer — all container logs in one UI |

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

### Redis Multi-Purpose
A single Redis instance serves three distinct use cases:
- Rate limiting at the gateway (`RequestRateLimiter`, token bucket)
- Cart session storage with TTL (`cart-service` — `RedisTemplate` hash ops, 30-min expiry)
- Product cache with eviction (`inventory-service` — Spring `@Cacheable` / `@CacheEvict`)

### Refresh Token Rotation
15-minute access tokens paired with 7-day refresh tokens. Every refresh issues a new token and revokes the old one — a stolen refresh token stops working the moment the legitimate user refreshes again. The Angular interceptor auto-refreshes transparently, so a user is never logged out unless idle for the full 7 days.

### Multi-Item Orders
A single order can hold multiple line items (`order_items` table) instead of one order per product. One Stripe `PaymentIntent` covers the full cart total, and one confirmation email summarizes all items. Inventory validates every item before any stock is deducted — it's all-or-nothing, so no customer ends up with a partially fulfilled order.

### Financial Audit Trail
Append-only PostgreSQL table — no UPDATE or DELETE ever.
All business events published to audit-topic via Kafka.
Consumed by audit-trail-service and persisted immutably.
Admin-only REST API with full event history.

### Token Rotation
Every refresh issues a new refresh token and revokes the old one.
Stolen tokens invalidated after first legitimate use.
Silent renewal in Angular interceptor — user never sees logout.

---

## Getting Started

### Prerequisites
- Java 21 (via SDKMAN: sdk install java 21.0.9-oracle)
- Node 24 (via nvm: nvm install 24 && nvm alias default 24)
- Docker + Docker Compose
- Maven 3.9+

### Local Development

**One command to start everything:**

```bash
./start-services.sh
```

> ℹ️ This script automatically starts Docker infrastructure
> (Kafka, PostgreSQL, Redis, Grafana, Mailpit), waits for
> each service to be healthy, then starts all 9 Spring Boot
> services and the Angular frontend.
>
> ⚠️ First time setup or after destroying volumes
> (`docker compose -f docker-compose.local.yml down -v`):
> PostgreSQL initializes automatically via `init-db.sql` —
> all databases and demo data are created on first start.
> No manual setup needed.

**Frontend:** http://localhost:4200

### Local Tools

| Tool | URL | Purpose |
|------|-----|---------|
| Shop UI | http://localhost:4200 | Angular frontend |
| Mailpit | http://localhost:8025 | Catch-all email inbox (dev) |
| Grafana | http://localhost:3000 | Traces + metrics |
| Dozzle | http://localhost:9999 | Real-time container logs |
| Audit Trail Service | http://localhost:8087 | Financial audit trail admin API |
| Config Server | http://localhost:8888 | Centralized config |

### Two Docker Compose Files

| File | Purpose |
|------|---------|
| docker-compose.local.yml | Local infra only — services run via Maven |
| docker-compose.yml | Full prod stack — all services as Docker images |

> Never run docker-compose.yml locally unless testing
> the full Docker deployment.

### Create Admin User (optional)
```bash
./scripts/create-admin.sh your@email.com yourpassword
```

### Demo Credentials
| User | Email | Password | Role |
|------|-------|----------|------|
| User | daniel@example.com | demo1234 | USER |
| Admin | admin@example.com | demo1234 | ADMIN |

### Test Payment (Stripe test mode)
| Card | Result |
|------|--------|
| 4242 4242 4242 4242 | Payment succeeds |
| 4000 0000 0000 0002 | Card declined |
| 4000 0025 0000 3155 | Requires 3D Secure |

---

## Scripts

### Manual Docker Hub Push
```bash
./scripts/push-all-to-dockerhub.sh
```
> Use only for full manual rebuild outside CI/CD pipeline.
> Normal deployments are handled automatically by Gitea Actions.

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
| Real-time logs | Dozzle (http://infra-node1:9999) | unified log viewer for all containers |

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
