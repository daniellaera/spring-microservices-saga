# Online Shop Microservices

A production-ready microservices-based online shop built with Spring Boot 4, demonstrating enterprise-grade patterns including event-driven saga, API gateway, and Docker deployment.

## Architecture
```
Client → Spring Cloud Gateway (8080)
              ├── /orders/**      → Order Service (8081)
              ├── /products/**    → Inventory Service (8082)
              └── /transactions/** → Payment Service (8083)
```

### Saga Flow
```
POST /orders → order-service → [orders-topic] → inventory-service → [inventory-topic] → payment-service → [payment-topic] → order-service
```

## Tech Stack

- **Java 21**
- **Spring Boot 4.0.2**
- **Spring Cloud Gateway** — API gateway and single entry point
- **Apache Kafka 4.2.0** — Event-driven communication (KRaft mode, no Zookeeper)
- **PostgreSQL 17** — Database per service
- **Flyway** — Database migrations
- **Springdoc OpenAPI 3** — API documentation
- **Docker** — Multi-arch images (linux/amd64, linux/arm64)
- **Lombok** — Boilerplate reduction

## Services

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| gateway-service | 8080 | - | API Gateway, single entry point |
| order-service | 8081 | orderdb | Manages orders |
| inventory-service | 8082 | inventorydb | Manages product stock |
| payment-service | 8083 | paymentdb | Processes payments |

## Prerequisites

- Java 21
- Docker + Docker Compose
- Maven 3.9+
- Docker Hub account (for pushing images)

## Running Locally

### Option 1 — Docker Compose (full stack)
```bash
docker compose -f compose-local.yml up --build
```

### Option 2 — IntelliJ + local infrastructure

Start only Kafka and PostgreSQL:
```bash
docker compose -f compose-local.yml up kafka postgres
```

Then run each service from IntelliJ.

## API Endpoints

All requests go through the gateway on port 8080.

### Orders
```
POST   http://localhost:8080/orders
GET    http://localhost:8080/orders
GET    http://localhost:8080/orders/{id}
```

### Products
```
POST   http://localhost:8080/products
GET    http://localhost:8080/products
```

### Swagger UI (direct service access)
```
http://localhost:8081/swagger-ui.html  — Order Service
http://localhost:8082/swagger-ui.html  — Inventory Service
http://localhost:8083/swagger-ui.html  — Payment Service
```

## Pushing to Docker Hub

### Prerequisites
```bash
docker login
```

### Build and push all services (multi-arch)
```bash
./push-to-dockerhub.sh
```

Builds for `linux/amd64` and `linux/arm64` and pushes to Docker Hub.

Images:
- `daniellaera/order-service:latest`
- `daniellaera/inventory-service:latest`
- `daniellaera/payment-service:latest`
- `daniellaera/gateway-service:latest`

## Project Structure
```
online-shop-microservices/
├── gateway-service/
├── order-service/
├── inventory-service/
├── payment-service/
├── docker/
│   └── init-db/
│       └── 01-init.sql
├── compose-local.yml
├── compose.yml
├── push-to-dockerhub.sh
└── pom.xml
```