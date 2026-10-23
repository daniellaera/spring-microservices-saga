# 13. Choreographed saga, not an orchestrator

Date: 2026-10-23

## Status

Accepted. Records a decision in effect since the first saga (May 2026), revised on 2026-06-25.

## Context

Placing an order spans three services with their own databases: `order-service` (orderdb), `inventory-service`
(inventorydb) and `payment-service` (paymentdb). There is no distributed transaction, so the order is a saga: a
sequence of local transactions, each with a compensation.

There are two ways to run a saga:

- **Orchestration:** one component (a state machine in `order-service`, or a workflow engine such as Temporal
  or Camunda) sends commands to each service and decides the next step and the compensations.
- **Choreography:** each service reacts to the events of the previous one and publishes its own result. No
  component knows the whole flow.

The saga has three steps and one compensation, and the services already communicate through Kafka
([ADR 2](0002-event-driven-communication-via-kafka.md)).

## Decision

The saga is choreographed through Kafka:

1. `order-service` stores the order (PENDING) and an outbox row in one transaction, and the relay publishes
   `orders-topic` ([ADR 16](0016-transactional-outbox-at-least-once.md)).
2. `inventory-service` (`inventory-group`) reserves the stock of every item, or rejects the order, and publishes
   APPROVED or REJECTED on `inventory-topic`.
3. `payment-service` (`payment-group`) verifies the PaymentIntent and publishes SUCCESS or FAILED on
   `payment-topic`.
4. `payment-topic` has three consumers: `order-service` (`order-group`) sets CONFIRMED or CANCELLED;
   `inventory-service` (`inventory-compensation-group`) releases the stock it reserved for a FAILED order;
   `notification-service` (`notification-group`) emails the customer.

On 2026-06-25 the order of the steps was corrected: inventory gates payment. Before, payment ran on
`orders-topic` alongside inventory, so a customer could be charged for stock that wasn't there, and compensation
restored stock that had never been reserved.

## Consequences

- **No single place shows the flow.** It is spread over four listeners. The audit trail
  ([ADR 11](0011-append-only-financial-audit-trail.md)) is the only cross-service view of one saga, and the
  Cucumber suite in `saga-e2e` (13 scenarios on real Kafka, PostgreSQL and Redis, Stripe stubbed) is its
  executable specification.
- **Every step must be idempotent, because every event can come twice.** That was not true until this week.
  Bugs found and fixed in 1.1.0, each local to one service:
  - compensation restored the requested quantities even when nothing was reserved (phantom stock, fixed on
    2026-10-21 with the `stock_reservation` table);
  - a redelivered inventory result was paid and recorded twice (now one transaction per order, by a unique
    constraint, and a redelivery republishes the stored outcome);
  - a late or duplicate payment result could change an order that was already final (status changes only
    from PENDING);
  - an order without a payment intent was confirmed for free;
  - compensation released stock row by row: a crash in the middle released part of it. It is now one
    transaction with the compensation log entry;
  - concurrent changes to one product (a reservation, a compensation and a restock run on different threads)
    lost updates: 10 orders of 1 against a stock of 5 were all approved in a test. Products are now locked
    (`SELECT … FOR UPDATE`) in name order, and a lock waits at most 3 s: a reservation is retried up to 3 times,
    then the order is rejected (and refunded) instead of waiting forever;
  - an order for an unknown product stayed PENDING forever; it is now REJECTED;
  - a failed order whose payment had been captured was never refunded ([ADR 17](0017-payment-compensation-by-refund.md)).
- **A step that never answers leaves the order PENDING.** There is no saga timeout. The places where a result
  can stop are now visible in the audit trail: an outbox row that can't be sent (`OUTBOX_EVENT_FAILED`), and an
  inventory result dead-lettered by payment-service (`PAYMENT_DEAD_LETTERED`, replayed by hand with
  `docs/maintenance/replay-dead-lettered-inventory-results.md`).
- **Adding a step means changing the services around it**, not one orchestrator. With three steps that is still
  cheaper than running and operating an orchestrator. A fourth step or a timeout policy would be the moment to
  reconsider orchestration.
