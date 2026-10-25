# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
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
- **A config server without the service's file is not detected**: config-server's native backend always answers
  with the shared `configs/application.yml`, so a renamed or missing `configs/<service>.yml` lets the service start
  on its local defaults (localhost database, no secrets) and fail later, unclearly. Options: config-server answers
  404 for an application without its own file (a change of its HTTP answer, to agree on first: every client and
  the config-server health check `app` request), or each client fails at startup without a property only its
  file sets.
- **The saga timeline query scans `audit_events`**: `GET /audit/orders/{id}/timeline` matches rows without a
  correlation id by entity and by `payload LIKE '%"orderId":N,%'` (ADR 18), so PostgreSQL cannot use the
  correlation id index alone. Harmless at today's size; once the table is large, or once no order older than
  2026-10-25 matters any more, drop the legacy branch or make it a separate, bounded query.
- **The USER_LOGOUT audit event carries the raw refresh token** as its payload (and no user): it is revoked by
  then, but a credential has no place in the audit trail. Record the user's email instead (the token's owner is
  known when it is found) and leave the token out. A change of the audit payload: agree on it first, since it is
  pinned by auth-service's contract fixtures (`auth-logout.json`).
