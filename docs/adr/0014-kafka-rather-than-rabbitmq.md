# 14. Kafka rather than RabbitMQ

Date: 2026-10-23

## Status

Accepted. Written on 2026-10-23 to record the trade-offs. No written comparison from the original choice
(May 2026) exists; [ADR 2](0002-event-driven-communication-via-kafka.md) only compares Kafka with REST.

## Context

The services need a broker for the saga and for the audit, OTP and notification events. The two common
choices are Apache Kafka, a partitioned log, and RabbitMQ, a message queue with exchanges and per-message
acknowledgement.

How the shop uses its broker:

- `payment-topic` has three consumer groups (order, inventory compensation, notification), and `audit-topic`
  receives events from four services (auth, order, inventory, payment) and is read by `audit-trail-service`.
- Every topic has one partition and every listener one thread. Volume is a few orders a day.
- Operators need to read what was sent after it was consumed: the dead-letter replay reads
  `inventory-topic-dlt` from the beginning, and the 2026-10-23 maintenance compared topics, end offsets and
  committed offsets before and after moving Kafka's data.

## Decision

Keep Apache Kafka 4.2 in KRaft mode, one broker.

## Consequences

**What Kafka gives us here**

- Fan-out is free: a new consumer group reads the same topic with no change to the producer.
- The log is kept after consumption, so a record can be read again, and "lag 0" on every group is a precise
  "nothing in flight" check (used before the 2026-10-23 maintenance and after the end-to-end test order).
- Ordering per partition: with one partition, events of a topic are processed in the order they were sent,
  which the outbox relies on.

**What it costs**

- **No per-message delay or redelivery.** RabbitMQ has dead-letter exchanges and message TTLs. With Kafka, a
  delayed retry needs retry topics: payment-service declares `@RetryableTopic`, which creates
  `inventory-topic-retry-0` to `-3` and `inventory-topic-dlt`. Getting that right took a fix in 1.1.0: the
  retries configured through `spring.kafka.retry.topic.*` ignored the listener's `@DltHandler`, and the
  default dead-letter handler failed on every record and looped (about 20,000 stack traces a minute).
- **Consumers own their offsets and their idempotency.** Delivery is at least once, so every consumer must
  handle duplicates ([ADR 13](0013-choreographed-saga.md)).
- **A JVM broker on a small host.** Kafka runs with a 1 GB heap on infra-node1, which has about 1 GB of memory
  available (BACKLOG.md).
- **State to look after.** The log directory was in the container's writable layer until 2026-10-23, so
  recreating the container would have lost every topic and offset, silently: Kafka formats an empty directory
  and starts healthy. It is now on the `kafka-data` volume.

**When to reconsider:** if per-message delays, priorities or routing by key become central, or if the host's
memory can't be raised. At the shop's volume either broker would cope.
