# 18. Per-order saga timeline from a correlation id on audit events

Date: 2026-10-25

## Status

Accepted

## Context

The saga is choreographed ([ADR 13](0013-choreographed-saga.md)): no component knows the whole of one order's
story. The audit trail ([ADR 11](0011-append-only-financial-audit-trail.md)) receives an event from every step,
so it is the one place that could show it, but its events could not be put together reliably:

- order and payment events carry the order id as their `entityId`, stock events the product id, and
  STOCK_DEDUCTED carried no order id at all, not even in its payload;
- the admin page loaded the 200 newest events and searched them in the browser, so an older order was out
  of reach;
- `createdAt` is UTC but sent without an offset, so the browser showed it as if it were local time (one or
  two hours off in Zurich).

The audit message on `audit-topic` and the `/audit` API are contracts: other services and the UI depend on
them, so they may only grow.

## Decision

**A correlation id, the order id, on every saga audit event.** order-service (ORDER_CREATED, ORDER_CONFIRMED,
ORDER_CANCELLED, OUTBOX_EVENT_FAILED), inventory-service (STOCK_DEDUCTED, STOCK_RESTORED) and payment-service
(PAYMENT_SUCCEEDED, PAYMENT_FAILED, PAYMENT_REFUNDED, PAYMENT_REFUND_FAILED, PAYMENT_DEAD_LETTERED) add a
`correlationId` field to the message. It is new and optional: the existing fields keep their meaning, and an
event outside a saga leaves the field out rather than sending null. PAYMENT_INITIATED has none, because the
intent is created before the order exists.

**audit-trail-service stores it** in a nullable `correlation_id` column (Flyway V2, indexed with
`created_at`). Messages without it, older ones included, are stored as before. The table is append-only, so
existing rows are **not backfilled**; the timeline query matches them the way they were stored instead: order
and payment events by `entity_id` (PAYMENT_INITIATED excepted), STOCK_RESTORED by the order id in its payload.
Older STOCK_DEDUCTED events cannot be matched.

**Two additions to the API:**

- `GET /audit` gets optional server-side filters (`q`, `eventType`, `serviceName`, `userEmail`, `orderId`,
  `from`, `to`), sorted newest first, with the page size capped at 200. Without filters it answers as before.
- `GET /audit/orders/{orderId}/timeline`: one order's events, oldest first.

Both are behind the gateway's admin check, like the rest of `/audit`.

**Time as an instant.** `createdAt` keeps its format; every event also gets `occurredAt`, the same instant with
its `Z`, and the service records `createdAt` in UTC explicitly instead of the JVM's zone. shop-ui shows
`occurredAt` in the browser's time zone, falling back to `createdAt` read as UTC.

**shop-ui**: the audit page paginates and filters on the server. Each saga event links to
`/audit/orders/:orderId`, a timeline with the outcome (CONFIRMED, CANCELLED, in progress), a COMPENSATED tag
when stock was restored or the payment refunded, and each step's time since the first one. Admins can also
open it from an order on the dashboard.

## Consequences

- The timeline is ordered by recording time in audit-trail-service, not by when each service acted. Steps
  that run concurrently (inventory and order-service both react to the payment result; payment-service
  audits asynchronously) can be recorded in either order. The saga e2e suite asserts them as one step.
- A lost audit event (Kafka unreachable, ADR 11) is a missing step in the timeline. The timeline shows what
  was recorded, not what happened.
- The `eventType` list in shop-ui's filter repeats `AuditEventType`. A new type needs both.
- Not in scope: the producer's own `timestamp` field (a local date-time without an offset) is still ignored;
  using it would mean changing its format, which is not additive.
