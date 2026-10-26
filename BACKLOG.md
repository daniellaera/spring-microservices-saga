# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **Run `docs/maintenance/hash-refresh-tokens-in-old-logout-events.sql` on auditdb** (dry run first, then
  `-v apply=true`): USER_LOGOUT events recorded before #45 (deployed 2026-10-25) still hold the raw, revoked
  refresh token; the script gives them the `"sha256:<12 hex>"` form. The header says what it does not reach
  (audit-topic until 2026-11-08, the backup below).
- **2026-10-30: delete the maintenance backups and review a week of memory on infra-node1.**
  - Delete `/srv/online-shop/maintenance/20261023/{kafka-logs,kafka-logs.tgz,grafana-data}` (3.4 GB; its
    `kafka-logs/audit-topic-0` also holds raw refresh tokens) and `/srv/online-shop/maintenance/20261024`, then
    remove the old anonymous Kafka volumes, checking each one first (see "Afterwards" in
    `docs/maintenance/kafka-and-grafana-volumes.md`).
  - Review a week of memory on the Grafana dashboard **infra-node1** (node-exporter + cAdvisor, since
    2026-10-24): memory available, swap used and pages swapped, memory per container and as a % of its
    `mem_limit`. Setup: 4 GB swap file (swappiness 10), a `mem_limit` on every container but shop-ui and
    dozzle, Kafka heap 512 MB; baseline `maintenance/20261024/memory.{before,after}` (1961 → 3236 MiB available).
    Decide whether the host needs more RAM before adding services: steady swap use, available memory under
    1 GiB, or a container near its limit say yes. The `sda` disk also has ~30 GB unallocated if the root volume
    needs to grow.
- **The saga timeline query scans `audit_events`**: `GET /audit/orders/{id}/timeline` matches rows without a
  correlation id by entity and by `payload LIKE '%"orderId":N,%'` (ADR 18), so PostgreSQL cannot use the
  correlation id index alone. Harmless at today's size; once the table is large, or once no order older than
  2026-10-25 matters any more, drop the legacy branch or make it a separate, bounded query.
- **order-service and inventory-service still send their saga audit events with `@Async`**: ORDER_CREATED and
  STOCK_DEDUCTED could in principle reach the audit topic after the next service's event, which would swap two
  steps of the saga timeline (payment-service had the same race, fixed after e2e run 295). Not seen so far:
  inventory-service's database work and payment-service's Stripe call come in between. Same fix if it shows up:
  send the event on the caller's thread before the message that starts the next step.
- **CI: a test JVM that does not exit within 30 s fails the coverage gate** (seen once, PR #47 job 859): every test
  of audit-trail-service passed, then its surefire fork was still alive 30 s after `System.exit(0)` and surefire
  killed it ("Surefire is going to kill self fork JVM"); aggregate coverage fell from 97.9% to 88.87% (gate 90%).
  The log does not show which thread held the exit (the last lines are OTLP metric pushes to the absent
  localhost:4318 collector). If it comes back: take a thread dump of the fork at exit (`jstack`, or surefire
  `forkedProcessExitTimeoutInSeconds` plus `-XX:+PrintConcurrentLocks` on a local `docker update --cpus` repro), and
  turn off the OTLP exporters in test configuration (`management.otlp.metrics.export.enabled=false`).

