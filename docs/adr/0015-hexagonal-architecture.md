# 15. Hexagonal architecture for the saga services

Date: 2026-10-22

## Status

Accepted. Applied to every domain service: order-service, inventory-service and payment-service (2026-10-22),
audit-trail-service, notification-service, cart-service and auth-service (2026-10-25). gateway-service and
config-server have no domain and keep their layout.

## Context

The three saga services were layered as controller, service and repository. The service classes called
Kafka, JPA and Stripe directly, so:

- most tests needed a Spring context, a database or a broker. Coverage was 74 % (order), 86 % (inventory) and
  77 % (payment);
- known saga bugs (an order without an intent was auto-approved, a redelivery was paid twice, a failed outbox
  send was marked published) could not be pinned or fixed without first untangling the code;
- CI had not actually run a test before 2026-10-22: Maven 3.8.7 bound a surefire that skipped JUnit 5, and
  every module reported 0 tests.

## Decision

Move the saga services to ports and adapters, one service per PR (order-service #3, inventory-service #5,
payment-service #9, all on 2026-10-22), as pure refactors: observable behaviour unchanged, known bugs
included. The behaviour fixes came afterwards, as separate PRs.

Layout, the same in each service:

```
domain/model          framework-free entities, value objects, exceptions
domain/port/in        use-case interfaces and their commands
domain/port/out       what the core needs: repositories, gateways, publishers, audit trail
application           use-case implementations
adapter/in/web        REST controllers, DTOs, web mappers
adapter/in/messaging  Kafka listeners
adapter/out/...       persistence (JPA), messaging (Kafka, outbox), stripe, notification
config                Spring wiring (@Bean per use case)
```

`HexagonalArchitectureTest` (ArchUnit 1.5.1) enforces in each service:

- `domain` depends only on `domain` and `java..`;
- `application` depends only on `domain`, `application`, `java..`, SLF4J and
  `org.springframework.transaction.annotation`;
- `domain` and `application` never depend on `adapter` or `config`;
- inbound adapters use ports, never application classes;
- adapters don't depend on each other (`adapter.(*).(*)` slices);
- in payment-service, only `adapter.out.stripe` uses `com.stripe`.

Each refactor started by recording the wire contracts (event JSON, audit payloads, REST error bodies) as
fixtures that had to stay byte-identical.

## Consequences

- Use cases are tested without Spring. After the three refactors the aggregate coverage was 95.5 %, and the
  coverage gate went from 85 % to 90 %. The saga fixes of 1.1.0 each came with tests on the use case first.
- More code: a domain model next to each JPA entity with a mapper, ports with one implementation, and a
  `@Bean` per use case.
- The rules force some unusual placements. inventory-service's audit adapter is two beans, because an `@Async`
  method runs on another thread and would no longer see the caller's transaction. The product-list cache
  invalidator lives in `adapter.in.web` next to the cache it owns, because adapters may not depend on each
  other.
- On 2026-10-25 the remaining domain services followed, the same way and under the same rules: audit-trail-service
  (#39), notification-service (#40), cart-service (#41) and auth-service (#42), each a pure refactor starting from
  contract fixtures (REST bodies, consumed and produced messages, stored rows, Redis values, sent emails) that did
  not change afterwards. auth-service was proven at the HTTP level: every endpoint and error, the audit and OTP
  events, the stored users, refresh tokens and OTP keys, with the JWTs verified and described. gateway-service
  (routing, JWT check, breakers) and config-server have no domain: they are not converted.
- Placements the rules force there: auth-service's UserDetailsService is Spring wiring in `config`, built from the
  `UserRepository` port, so that the JPA entity no longer implements Spring Security's `UserDetails`; its refresh
  rotation lives in the application layer and calls `RefreshTokenService` through its bean, keeping one transaction
  per step as before. notification-service's email wording is in the application layer (`OrderEmails`), the SMTP
  details in `adapter.out.mail`. cart-service's Redis value stays the same plain JSON (`StoredCartItem`).
