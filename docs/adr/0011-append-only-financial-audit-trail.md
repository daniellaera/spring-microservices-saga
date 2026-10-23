# 11. Append-only financial audit trail

Date: 2026-07-16

## Status

Accepted

## Context

Financial systems need a complete, tamper-evident log of every business event, as regulations such as
MiFID II, FINMA rules and SOX require.

## Decision

A dedicated `audit-trail-service` with an append-only PostgreSQL table: no UPDATE or DELETE. Every business
service publishes its events fire-and-forget to `audit-topic`.

**Why a separate service?**

- Single responsibility: audit logic stays out of business logic.
- Independent scaling: audit writes don't load the business services.
- Failure isolation: an audit failure never breaks the business flow.
- Admin-only access, through the gateway's role check.

Event sources:

- `auth-service`: USER_LOGIN, USER_LOGOUT, USER_REGISTER, TOKEN_REFRESHED;
- `order-service`: ORDER_CREATED, ORDER_CONFIRMED, ORDER_CANCELLED;
- `payment-service`: PAYMENT_INITIATED, PAYMENT_SUCCEEDED, PAYMENT_FAILED;
- `inventory-service`: STOCK_DEDUCTED, STOCK_RESTORED, PRODUCT_RESTOCKED.

## Consequences

- Fire-and-forget means an audit event can be lost while Kafka is unreachable. The services use fail-fast
  producers (`max.block.ms` 1 s), so the business flow never waits for the audit.
- The audit trail is the only place that shows a whole saga across services, so it doubles as the operators'
  view. 1.1.0 adds PAYMENT_REFUNDED, PAYMENT_REFUND_FAILED and PAYMENT_DEAD_LETTERED for the cases that need
  a person.
- Stock events use the product id as `entityId`, not the order id, so they can't be found by order id.
- On 2026-10-23 the admin `/audit` page looked like it had lost events. It hadn't: the events were in
  `auditdb`, and the page had four frontend bugs (fixed in 1.1.0).
