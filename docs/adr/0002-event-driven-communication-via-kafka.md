# 2. Event-driven communication via Kafka

Date: 2026-06-25

## Status

Accepted. The broker choice against RabbitMQ is recorded in [ADR 14](0014-kafka-rather-than-rabbitmq.md).

## Context

Order processing coordinates three independent services: order, inventory and payment. Direct REST calls
between them would couple them tightly, and an event would be lost whenever the callee is down.

## Decision

Use Apache Kafka for all communication between services in the order saga. Services publish to topics and
consume asynchronously.

**Why Kafka over REST?** Kafka decouples services in time: if `payment-service` is down, the message stays in
the topic and is consumed when it comes back. With REST, a failed call loses the event. Kafka also lets several
consumers read the same topic: `order-service`, `inventory-service` (compensation) and `notification-service`
all consume `payment-topic`.

**Why KRaft (no ZooKeeper)?** Kafka 4.x runs in KRaft mode by default. It removes the ZooKeeper dependency
for local development and production.

## Consequences

- Every topic has one partition (the broker default `KAFKA_NUM_PARTITIONS: 1`) and every listener one
  thread. Events of a topic are processed in order, one at a time; there is no parallelism within a topic.
- Consumers must tolerate redelivery: delivery is at least once (see [ADR 16](0016-transactional-outbox-at-least-once.md)).
- The broker is stateful infrastructure. Until 2026-10-23 its log directory was in the container's writable
  layer, so recreating the container would have deleted every topic and consumer offset. It now lives on the
  `kafka-data` volume (`docs/maintenance/kafka-and-grafana-volumes.md`).
