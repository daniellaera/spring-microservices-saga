# Roadmap

## ✅ Done

- [x] Spring Boot 4 + Java 21 multi-module Maven project
- [x] Event-driven Saga pattern with Kafka (order → inventory → payment)
- [x] Spring Cloud Gateway with JWT validation
- [x] Auth service — register, login, BCrypt, JWT generation with role claim
- [x] **Role-Based Access Control (RBAC)** — USER and ADMIN roles enforced at the gateway
- [x] Spring Cloud Config Server — centralized configuration
- [x] Dev/prod profiles managed via Config Server
- [x] OpenTelemetry — distributed tracing + metrics via `spring-boot-starter-opentelemetry`
- [x] Grafana LGTM stack — Tempo, Loki, Mimir
- [x] Docker Compose — local dev + prod-like full stack
- [x] Multi-arch Docker Hub images (linux/amd64 + linux/arm64)
- [x] Deployed on infra-node1 with Nginx reverse proxy
- [x] Flyway migrations — database per service pattern
- [x] Springdoc OpenAPI — Swagger UI per service
- [x] Notification service — Kafka consumer on `payment-topic`
- [x] Circuit Breaker — Resilience4j on gateway (CLOSED/OPEN/HALF_OPEN states)
- [x] JSON messaging — standardized event format across all Kafka topics
- [x] Frontend service — Thymeleaf + HTMX UI (login, orders, products)
- [x] Rate limiting — Redis token bucket on gateway (10 req/s, burst 20)
- [x] Redis cache — `@Cacheable` on inventory products, `@CacheEvict` on create
- [x] **Unit tests with JUnit + Mockito** — services and controllers across all modules
- [x] **Integration tests with Testcontainers** — PostgreSQL + Kafka + Redis per service

## 📋 Upcoming

### Resilience
- [ ] Retry mechanism — Kafka consumer retry + Dead Letter Topic handling
- [ ] Timeout configuration per route on the gateway
- [ ] Saga compensation — rollback inventory when payment fails

### Security
- [ ] Refresh token mechanism
- [ ] ADMIN-only endpoint `POST /auth/admin` to create new admins (requires existing ADMIN JWT)

### Quality
- [ ] SonarQube analysis in CI pipeline

### Architecture
- [ ] Custom Spring Boot starter — encapsulate OpenTelemetry config as a reusable starter
- [ ] OpenRewrite recipe — automated migration tooling demo
- [ ] Quarkus service — add one service in Quarkus for comparison with Spring Boot
- [ ] Service Discovery — Spring Cloud Eureka or Consul

### DevOps
- [ ] GitHub Actions CI/CD pipeline — build, test, push to Docker Hub on merge
- [ ] Kubernetes manifests (Helm chart)