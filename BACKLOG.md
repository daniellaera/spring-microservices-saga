# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **infra-node1 memory headroom**: 7.9 GB RAM. Since 2026-10-24: a 4 GB swap file (swappiness 10), every
  container except shop-ui and dozzle has a `mem_limit`, and Kafka runs with a 512 MB heap. Watch
  `free -m` and swap use for a week, then decide whether the host needs more RAM before adding services (the
  `sda` disk also has ~30 GB unallocated, if the root volume needs to grow).
- **`/orders/stream` answers 500 when order-service is down**: the SSE route (`order-stream` in the
  gateway's `application.yml`) has no CircuitBreaker filter, unlike `/orders/**`, so a browser reconnecting
  during an order-service restart gets a 500 with a stack trace in the gateway log (seen during the v1.1.0
  deploy, 2026-10-23 19:27). Give it a fallback answering 503 (an SSE client retries on its own), without the
  1 s time limit, which would cut a long-lived stream.
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
