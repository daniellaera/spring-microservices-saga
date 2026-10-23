# 16. Transactional outbox with at-least-once delivery

Date: 2026-10-23

## Status

Accepted. Replaces the fire-and-forget relay that order-service used until 1.1.0.

## Context

`POST /orders` must store the order and announce it on `orders-topic`. Writing to PostgreSQL and then sending
to Kafka is a dual write: a crash or a broker outage between the two loses the event, and the order stays
PENDING forever.

order-service already had an outbox: the order and an `outbox_events` row are written in one transaction, and
a scheduled relay publishes the rows. But the relay could still lose events. `KafkaOrderEventSender` called
`kafkaTemplate.send(...)` without waiting and swallowed any failure, and the relay marked the row published
anyway. While Kafka was unreachable, an order event could disappear without a trace.

## Decision

The relay (`OutboxRelay`, every 5 s, in creation order) delivers each row **at least once**:

- **The send is synchronous with a bounded wait.** The sender waits for Kafka's acknowledgement for up to
  `outbox.relay.send-timeout` (5 s). The producer itself gives up sooner (`max.block.ms` 1 s,
  `delivery.timeout.ms` 2 s).
- **A row is marked published only after the acknowledgement.**
- **A failed send stops the batch.** No later event overtakes it, and the row is retried on the next tick.
- **A crash between the acknowledgement and the commit sends the event again.** That is the "at least once":
  `inventory-service` skips an order it has already reserved (`existsByOrderId`), so a duplicate is harmless.
- **A row whose payload can't be read** can never be sent. It must not block every later order, so it is
  skipped and later rows still go out. <!-- phase 2: attempts counter, FAILED status and audit event -->

## Consequences

- No silent loss: with Kafka down, rows stay unpublished and go out in order once it is back. An IT pauses a
  Kafka container to prove it; the old relay fails that IT.
- Consumers must be idempotent, which the saga requires anyway ([ADR 13](0013-choreographed-saga.md)).
- One stuck row holds back the rows after it, up to the send timeout per tick. That is the price of ordering.
- The relay is a single scheduled thread in a single instance. Several instances would need row locking
  (`FOR UPDATE SKIP LOCKED`) to avoid sending the same row twice at the same time.
- Alternatives not taken: change data capture (Debezium) would add a component to run; Kafka transactions
  don't cover the PostgreSQL write.
