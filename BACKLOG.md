# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **infra-node1 memory is tight**: 1.1 GB available of 7.9 GB, no swap (2026-10-23, 16 containers).
  Kafka alone runs with `-Xmx1G -Xms1G`. Add swap or memory, or cap the JVM heaps, before adding services.
- **Run the shop-ui Playwright specs in CI** (`shop-ui/e2e`, stack in `e2e/stack/compose.yml`): wire it
  in with the checkout-modal PR. Today they run locally only.
- **`ng test` fails on main**: `src/app/app.spec.ts` (Angular CLI scaffold) has no provider for
  `ConfirmationService`. CI only runs `ng build`, so nothing notices. Fix or delete the spec, then run
  `ng test` in CI.
- **Move Kafka and Grafana data onto volumes**: see `docs/maintenance/kafka-and-grafana-volumes.md`
  (planned window, not executed).
