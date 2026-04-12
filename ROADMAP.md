# Roadmap

## Done

- [x] Spring Boot 4 + Java 21 multi-module Maven project
- [x] Event-driven Saga pattern with Kafka (order → inventory → payment)
- [x] Spring Cloud Gateway with JWT validation
- [x] Auth service — register, login, BCrypt, JWT generation
- [x] Spring Cloud Config Server — centralized configuration
- [x] OpenTelemetry — distributed tracing + metrics via `spring-boot-starter-opentelemetry`
- [x] Grafana LGTM stack — Tempo, Loki, Mimir
- [x] Docker Compose — local dev + prod-like full stack
- [x] Multi-arch Docker Hub images (linux/amd64 + linux/arm64)
- [x] Deployed on infra-node1 with Nginx reverse proxy
- [x] Flyway migrations — database per service pattern
- [x] Springdoc OpenAPI — Swagger UI per service
- [x] Notification service — Kafka consumer on `payment-topic`
- [x] Circuit Breaker — Resilience4j on gateway with CLOSED/OPEN/HALF_OPEN states
- [x] JSON messaging — standardized event format across all Kafka topics

## Upcoming

### Resilience
- [ ] Rate limiting on the gateway

### Quality
- [ ] Unit tests with JUnit + Mockito per service
- [ ] Integration tests with Testcontainers
- [ ] SonarQube analysis in CI pipeline

### Architecture
- [ ] Custom Spring Boot starter — encapsulate OpenTelemetry config as a reusable starter
- [ ] OpenRewrite recipe — automated migration tooling demo
- [ ] Quarkus service — add one service in Quarkus for comparison with Spring Boot

### DevOps
- [ ] GitHub Actions CI/CD pipeline — build, test, push to Docker Hub on merge
- [ ] Kubernetes manifests (Helm chart)