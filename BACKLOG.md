# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **infra-node1 memory is tight**: 1.1 GB available of 7.9 GB, no swap (2026-10-23, 16 containers).
  Kafka alone runs with `-Xmx1G -Xms1G`. Add swap or memory, or cap the JVM heaps, before adding services.
- **Order 59 may hold an unrefunded payment**: cancelled on 2026-10-21 (inventory REJECTED) before payment
  compensation existed; its intent `pi_3USvDh…` (€15,999.80) may have been captured. Check it in the Stripe
  dashboard and refund it there if it was.
- **Clean up after the Kafka/Grafana volume move (2026-10-23), from 2026-10-30**: delete
  `/srv/online-shop/maintenance/20261023/{kafka-logs,kafka-logs.tgz,grafana-data}` (3.4 GB; 9.9 GB free on `/`
  until then), then remove the old anonymous Kafka volumes, checking each one first (see "Afterwards" in
  `docs/maintenance/kafka-and-grafana-volumes.md`).
- **Warm the Stripe client when payment-service starts**: the first Stripe call after a restart took over 1 s
  (the gateway 404 of 2026-10-23). The gateway now allows 10 s, so this is only latency for the first buyer.
- **Dormant DLT handlers in inventory-service and order-service:** their listeners declare
  `@DltHandler handleDlt(String, Exception)`, but they have no retry topics, so the handlers never run. If retries
  are ever enabled there, declare them with `@RetryableTopic` on the listener, as payment-service does.
  `spring.kafka.retry.topic.*` ignores the class's `@DltHandler` and uses Spring Kafka's default one, which fails
  on every record under `ack-mode: record` and loops forever. Also, an `Exception` parameter can't be resolved
  there: read the `kafka_exception-message` header from the `ConsumerRecord` instead.
