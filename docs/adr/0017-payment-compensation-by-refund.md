# 17. Payment compensation by refund

Date: 2026-10-23

## Status

Accepted

## Context

The customer pays in the browser before the order exists ([ADR 5](0005-stripe-payment-integration.md)): by the
time the saga runs, Stripe has already captured the money. When the saga then fails, the order is cancelled,
but until 1.1.0 nothing gave the money back:

- **Inventory rejects the order** (not enough stock). payment-service didn't even look at the intent.
- **The intent is for another amount or currency** than the order. The order fails, and the money stays
  captured.

One production order matches: order 59 (CANCELLED on 2026-10-21, €15,999.80) was rejected for stock, and its
payment may never have been refunded. Only the Stripe dashboard can tell.

Refunding has its own pitfalls:

- an intent can be presented for two orders. If it paid order A, refunding it when order B fails would take
  back A's money;
- Stripe still reports a refunded intent as `succeeded`, so it could then pay for another order;
- Stripe can be briefly unavailable (no answer, 429, 5xx). Recording the order as failed then would be wrong,
  because the payment may be fine.

## Decision

In `ProcessInventoryResultService` (payment-service):

1. **Refund only a captured payment of a failing order.** The intent must belong to no other order, the order
   must end FAILED (inventory REJECTED, or an amount or currency mismatch), and Stripe must report the intent
   `succeeded`.
2. **Never refund an intent that paid for another order.** That money pays for the other order.
3. **One refund per order.** It refunds the whole intent with `Idempotency-Key: refund-order-<orderId>`, so a
   retry or a racing delivery makes at most one refund.
4. **Record it.** `transaction.refund_id` (Flyway V5) stores the refund id, and the FAILED row keeps the
   refunded intent, so the intent can never pay another order. Audited as `PAYMENT_REFUNDED`
   `{orderId, paymentIntentId, refundId}`. The event on `payment-topic` stays byte-identical (FAILED): the rest
   of the saga doesn't change.
5. **Edge cases:**
   - Stripe's `charge_already_refunded` (refunded by hand) is recorded as refunded, with the marker
     `already_refunded` and a WARN log;
   - any other refusal is recorded as FAILED without a refund id, logged at ERROR, and audited as
     `PAYMENT_REFUND_FAILED` `{orderId, paymentIntentId, errorCode}` for a manual refund.
6. **A transient Stripe error is retried through Kafka, not recorded.** `PaymentGatewayUnavailableException`
   propagates to the listener. `@RetryableTopic` retries the inventory result 5 times, after 2, 6, 18 and 54 s
   (about 80 s), on `inventory-topic-retry-0..3`. The retries don't block: other orders are processed while one
   waits. After the last attempt the record goes to `inventory-topic-dlt`, where `handleDlt` logs it once and
   audits `PAYMENT_DEAD_LETTERED`. The order stays PENDING until someone replays it
   (`docs/maintenance/replay-dead-lettered-inventory-results.md`).

The retries used to be configured with Spring Boot's `spring.kafka.retry.topic.*`. That configuration ignores
the listener's `@DltHandler` and uses Spring Kafka's default handler, which needs an `Acknowledgment` that
`ack-mode: record` doesn't provide. Every dead-lettered record failed and was retried in a loop (about 20,000
stack traces a minute), and its offset was never committed. Declaring `@RetryableTopic` on the listener wires
the real handler.

## Consequences

- A failed order no longer keeps the customer's money, and every refund is traceable from the order id
  (`PAYMENT_REFUNDED`, `refund_id`).
- `PAYMENT_REFUND_FAILED` and `PAYMENT_DEAD_LETTERED` need a person. They are visible on the admin `/audit`
  page; there is no alerting yet.
- A Stripe outage longer than about 80 s leaves orders PENDING until a manual replay. That was preferred to
  failing orders whose payment may be fine.
- Past orders are not refunded retroactively (order 59 is a BACKLOG item).
- Alternative not taken: authorize in the browser and capture only after inventory approves
  (`capture_method=manual`). No money would move for a rejected order, but checkout would need a capture step
  in the saga and handle authorizations that expire.
- Fewer stray intents: since 1.1.0 the checkout keeps one intent per purchase, so a retried "Buy now" (for
  example after the gateway's old 1 s timeout on `/payments`) no longer creates a second one.
