# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **Order 59 may hold an unrefunded payment**: cancelled on 2026-10-21 (inventory REJECTED) before payment
  compensation existed; its intent `pi_3USvDh…` (€15,999.80) may have been captured. Check it in the Stripe
  dashboard and refund it there if it was.
- **2026-10-30: delete the maintenance backups and review a week of memory on infra-node1.**
  - Delete `/srv/online-shop/maintenance/20261023/{kafka-logs,kafka-logs.tgz,grafana-data}` (3.4 GB) and
    `/srv/online-shop/maintenance/20261024`, then remove the old anonymous Kafka volumes, checking each one first
    (see "Afterwards" in `docs/maintenance/kafka-and-grafana-volumes.md`).
  - Review a week of memory on the Grafana dashboard **infra-node1** (node-exporter + cAdvisor, since
    2026-10-24): memory available, swap used and pages swapped, memory per container and as a % of its
    `mem_limit`. Setup: 4 GB swap file (swappiness 10), a `mem_limit` on every container but shop-ui and
    dozzle, Kafka heap 512 MB; baseline `maintenance/20261024/memory.{before,after}` (1961 → 3236 MiB available).
    Decide whether the host needs more RAM before adding services: steady swap use, available memory under
    1 GiB, or a container near its limit say yes. The `sda` disk also has ~30 GB unallocated if the root volume
    needs to grow.
- **Dead-lettered records in inventory-service and order-service are only logged** (since 2026-10-25): no audit
  event and no replay runbook, unlike payment-service's `PAYMENT_DEAD_LETTERED` and
  `docs/maintenance/replay-dead-lettered-inventory-results.md`. A dead-lettered order stays PENDING, a
  dead-lettered compensation leaves the stock deducted. Watch for `DLT received failed message` in the logs of
  both services, and replay from `orders-topic-inventory-dlt`, `payment-topic-inventory-dlt` or
  `payment-topic-order-dlt` the same way.
