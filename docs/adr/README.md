# Architecture Decision Records

Each record follows Michael Nygard's format: title, date, status, context, decision, consequences. A record
is not rewritten when a decision changes: a new record supersedes it.

ADRs 1 to 12 were migrated from `docs/ARCHITECTURE.md` on 2026-10-23 with their decisions and dates (the dates
of the commits that added them). There is no ADR 7: the numbering in `ARCHITECTURE.md` skipped it. ADR 12
restates ADR 8; both are kept so that existing references stay valid.

| # | Title | Date | Status |
|---|---|---|---|
| 1 | [Real-time order updates via SSE, not polling](0001-sse-for-real-time-order-updates.md) | 2026-06-25 | Accepted |
| 2 | [Event-driven communication via Kafka](0002-event-driven-communication-via-kafka.md) | 2026-06-25 | Accepted |
| 3 | [Centralized configuration via Spring Cloud Config](0003-centralized-configuration-via-spring-cloud-config.md) | 2026-06-25 | Accepted |
| 4 | [RBAC at the gateway layer](0004-rbac-at-the-gateway.md) | 2026-06-25 | Accepted |
| 5 | [Stripe payment integration](0005-stripe-payment-integration.md) | 2026-06-25 | Accepted |
| 6 | [Infisical self-hosted secrets management](0006-infisical-self-hosted-secrets.md) | 2026-06-25 | Accepted |
| 8 | [Refresh token rotation](0008-refresh-token-rotation.md) | 2026-07-15 | Accepted |
| 9 | [Redis multi-purpose strategy](0009-redis-multi-purpose.md) | 2026-07-15 | Accepted |
| 10 | [Multi-item orders with a single PaymentIntent](0010-multi-item-orders-single-payment-intent.md) | 2026-07-15 | Accepted |
| 11 | [Append-only financial audit trail](0011-append-only-financial-audit-trail.md) | 2026-07-16 | Accepted |
| 12 | [Refresh token rotation (restated)](0012-refresh-token-rotation-restated.md) | 2026-07-16 | Accepted, restates 8 |
| 13 | [Choreographed saga, not an orchestrator](0013-choreographed-saga.md) | 2026-10-23 | Accepted |
| 14 | [Kafka rather than RabbitMQ](0014-kafka-rather-than-rabbitmq.md) | 2026-10-23 | Accepted |
| 15 | [Hexagonal architecture for the saga services](0015-hexagonal-architecture.md) | 2026-10-22 | Accepted |
| 16 | [Transactional outbox with at-least-once delivery](0016-transactional-outbox-at-least-once.md) | 2026-10-23 | Accepted |
| 17 | [Payment compensation by refund](0017-payment-compensation-by-refund.md) | 2026-10-23 | Accepted |
| 18 | [Per-order saga timeline from a correlation id on audit events](0018-saga-timeline-correlation-id.md) | 2026-10-25 | Accepted |

Secrets management with Infisical was already recorded as ADR 6, so it has no new record.

To add a record, copy the structure of an existing one, take the next number, and add a row here.
