# Online Shop Microservices

A production-ready microservices-based online shop built with Spring Boot 4, demonstrating enterprise-grade patterns including event-driven saga, JWT authentication, distributed tracing, centralized configuration, API gateway, circuit breaker, rate limiting, and Redis caching.

![Architecture](./docs/architecture_overview_banner.svg)
![Saga Flow](./docs/saga_flow_banner.svg)

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [Architecture](./docs/ARCHITECTURE.md) | System design, flows, and tech decisions |
| [Roadmap](./docs/ROADMAP.md) | What's done and what's coming |

---

## Architecture

```
Client / Frontend UI :8090
         │ JWT
         ▼
Spring Cloud Gateway :8080
  JWT Validation · Routing · Circuit Breaker · Rate Limiting
         │
         ├── /auth/**         → Auth Service :8084
         ├── /orders/**       → Order Service :8081
         ├── /products/**     → Inventory Service :8082
         └── /transactions/** → Payment Service :8083

Config Server :8888  ← all services fetch config at startup
Grafana LGTM  :3000  ← traces, metrics, logs via OpenTelemetry
Redis         :6379  ← rate limiting + product cache
```

### Saga Flow (Event-Driven)
```
POST /orders
  → order-service → [orders-topic]
    → inventory-service → checks stock → [inventory-topic]
      → payment-service → processes payment → [payment-topic]
        → order-service → CONFIRMED or FAILED
        → notification-service → logs confirmation
```

### Authentication Flow
```
POST /auth/register → auth-service → BCrypt → JWT
POST /auth/login    → auth-service → validate → JWT
GET  /orders        → gateway validates JWT → order-service
GET  /orders        → no token → 401 Unauthorized
```

---

## Tech Stack

| Technology | Version | Usage |
|------------|---------|-------|
| Java | 21 | Virtual threads, records, sealed classes |
| Spring Boot | 4.0.2 | Core framework |
| Spring Cloud Gateway | 2025.1.1 | API gateway, routing, JWT validation |
| Spring Cloud Config | 2025.1.1 | Centralized configuration |
| Spring Security | 7 | JWT authentication |
| Resilience4j | latest | Circuit Breaker (CLOSED/OPEN/HALF_OPEN) |
| Apache Kafka | 4.2.0 | Event-driven communication (KRaft, no Zookeeper) |
| PostgreSQL | 17 | Database per service pattern |
| Redis | 8 | Rate limiting (token bucket) + product cache |
| Flyway | 11 | Database migrations |
| OpenTelemetry | latest | Distributed tracing + metrics |
| Grafana LGTM | latest | Observability (Tempo, Loki, Mimir) |
| Thymeleaf + HTMX | latest | Lightweight frontend UI |
| Docker | latest | Multi-arch images (amd64 + arm64) |
| Lombok | latest | Boilerplate reduction |
| Springdoc OpenAPI | 3 | Swagger UI per service |

---

## Services

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| gateway-service | 8080 | - | API Gateway, JWT, Circuit Breaker, Rate Limiter |
| auth-service | 8084 | authdb | Register, login, JWT generation |
| order-service | 8081 | orderdb | Manages orders, publishes to orders-topic |
| inventory-service | 8082 | inventorydb | Stock management, Redis cache, consumes orders-topic |
| payment-service | 8083 | paymentdb | Payment processing, consumes inventory-topic |
| notification-service | 8085 | - | Consumes payment-topic, logs confirmations |
| frontend-service | 8090 | - | Thymeleaf + HTMX UI |
| config-server | 8888 | - | Centralized Spring Cloud Config Server |

---

## Observability

Every request is automatically traced end-to-end across all services using OpenTelemetry.

- **Traces** → Grafana Tempo
- **Metrics** → Grafana Mimir
- **Logs** → Grafana Loki

Each log line includes a `traceId` for correlation:
```
[order-service] [nio-8081-exec-1] [f9b4100b3004d2e68a306bf2862c67f1-7b3daefca6eeca53] ...
```

Access Grafana at `http://localhost:3000`

---

## Centralized Configuration

All services fetch config from the Config Server at startup. Dev/prod profiles managed centrally.

```
config-server
  └── configs/
       ├── application.yml        ← shared: OpenTelemetry, tracing
       ├── application-dev.yml    ← shared dev: DEBUG logging
       ├── gateway-service.yml
       ├── order-service.yml
       ├── inventory-service.yml
       ├── payment-service.yml
       ├── auth-service.yml
       ├── notification-service.yml
       └── frontend-service.yml
```

---

## Prerequisites

- Java 21
- Docker + Docker Compose
- Maven 3.9+

---

## Running Locally

### Option 1 — Docker Compose (full stack)
```bash
docker compose -f docker-compose.yml up --build
```

### Option 2 — IntelliJ + local infrastructure

Start infrastructure (Kafka, PostgreSQL, Grafana, Redis):
```bash
docker compose -f docker-compose.local.yml up
```

Then run services from IntelliJ or use the startup script:
```bash
chmod +x start-services.sh
./start-services.sh
```

**Startup order:**
1. `config-server`
2. `auth-service`, `order-service`, `inventory-service`, `payment-service`, `notification-service`
3. `gateway-service`
4. `frontend-service` → UI at `http://localhost:8090`

---

## API Endpoints

All requests go through the gateway on port `8080`.

### Authentication (public)
```
POST   http://localhost:8080/auth/register    ← returns JWT token
POST   http://localhost:8080/auth/login       ← returns JWT token
```

### Orders (requires JWT)
```
POST   http://localhost:8080/orders
GET    http://localhost:8080/orders
GET    http://localhost:8080/orders/{id}
```

### Products (requires JWT)
```
POST   http://localhost:8080/products
GET    http://localhost:8080/products          ← cached in Redis
```

### Example usage
```bash
# 1. Register and get token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName": "Daniel", "lastName": "Laera", "email": "daniel@test.com", "password": "password123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 2. Create a product
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name": "MacBook Pro M4", "quantity": 10}'

# 3. Place an order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productName": "MacBook Pro M4", "quantity": 1}'

# 4. Check orders
curl http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN"

# 5. Test rate limiting (sends 25 parallel requests)
for i in {1..25}; do
  curl -s -o /dev/null -w "Request $i: %{http_code}\n" http://localhost:8080/orders \
    -H "Authorization: Bearer $TOKEN" &
done
wait
```

### Swagger UI (direct service access)
```
http://localhost:8081/swagger-ui.html  — Order Service
http://localhost:8082/swagger-ui.html  — Inventory Service
http://localhost:8083/swagger-ui.html  — Payment Service
```

---

## Circuit Breaker

```bash
# Check circuit breaker states
curl http://localhost:8080/actuator/circuitbreakers
```

States: `CLOSED` → `OPEN` (>50% failures) → `HALF_OPEN` (after 10s) → `CLOSED`

---

## Rate Limiting

```bash
# Test rate limiting (429 after burst capacity exceeded)
for i in {1..25}; do
  curl -s -o /dev/null -w "Request $i: %{http_code}\n" http://localhost:8080/orders \
    -H "Authorization: Bearer $TOKEN" &
done
wait
```

---

## Docker Hub Images

```
daniellaera/config-server:latest
daniellaera/auth-service:latest
daniellaera/gateway-service:latest
daniellaera/order-service:latest
daniellaera/inventory-service:latest
daniellaera/payment-service:latest
daniellaera/notification-service:latest
daniellaera/frontend-service:latest
```

---

## Project Structure

```
online-shop/
├── auth-service/
├── config-server/
├── frontend-service/
├── gateway-service/
├── inventory-service/
├── notification-service/
├── order-service/
├── payment-service/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ROADMAP.md
│   ├── architecture_overview_banner.svg
│   └── saga_flow_banner.svg
├── docker-compose.yml        ← full stack (prod-like)
├── docker-compose.local.yml  ← infrastructure only (dev)
├── push-to-dockerhub.sh
├── start-services.sh
└── pom.xml
```