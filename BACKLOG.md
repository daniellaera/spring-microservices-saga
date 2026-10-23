# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **infra-node1 memory is tight**: 1.1 GB available of 7.9 GB, no swap (2026-10-23, 16 containers).
  Kafka alone runs with `-Xmx1G -Xms1G`. Add swap or memory, or cap the JVM heaps, before adding services.
- **Move Kafka and Grafana data onto volumes**: see `docs/maintenance/kafka-and-grafana-volumes.md`
  (planned window, not executed).
- **Lock timeout for the product row locks** (inventory-service): the pessimistic locks taken by
  reservation, compensation and restock (`findBy…ForUpdate`) wait forever by default. Set a lock timeout of a
  few seconds (`jakarta.persistence.lock.timeout` hint on the finders, or PostgreSQL `lock_timeout`), so
  that a stuck transaction fails fast instead of blocking orders.
- **Outbox dead-letter handling** (order-service): a row whose payload can't be parsed is logged and
  skipped on every 5 s tick, forever. Add an attempts counter or a FAILED status column, plus an alert,
  instead of retrying it endlessly.
- **Dormant DLT handlers in inventory-service and order-service:** their listeners declare
  `@DltHandler handleDlt(String, Exception)`, but they have no retry topics, so the handlers never run. If retries
  are ever enabled there, declare them with `@RetryableTopic` on the listener, as payment-service does.
  `spring.kafka.retry.topic.*` ignores the class's `@DltHandler` and uses Spring Kafka's default one, which fails
  on every record under `ack-mode: record` and loops forever. Also, an `Exception` parameter can't be resolved
  there: read the `kafka_exception-message` header from the `ConsumerRecord` instead.
