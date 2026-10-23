# Backlog

- **Move all API routes under `/api`** (gateway routes + shop-ui `nginx.conf` + shop-ui services). The
  API paths `/products` and `/audit` are also Angular routes, so nginx tells page loads from API calls
  by the `Accept: text/html` header (`$spa_navigation` in `shop-ui/config/nginx.conf`). A separate `/api`
  prefix removes that workaround.
- **infra-node1 memory is tight**: 1.1 GB available of 7.9 GB, no swap (2026-10-23, 16 containers).
  Kafka alone runs with `-Xmx1G -Xms1G`. Add swap or memory, or cap the JVM heaps, before adding services.
- **Run the shop-ui Playwright specs in CI** (`shop-ui/e2e`, stack in `e2e/stack/compose.yml`): wire it
  in with the checkout-modal PR. Today they run locally only.
- **shop-ui checkout PR** (checkout modal, `features/dashboard`), from the order 67 and first "Buy now"
  findings of 2026-10-23:
  - **Silent quantity clamp on blur**: PrimeNG `p-inputNumber` applies `[max]` (the stock) only on blur, so
    a typed 8 with 7 in stock shows 8 and its total until "Buy now" clamps it to 7 as the dialog opens.
    While quantity > stock, disable "Buy now" and "Add to cart" and show "Only N left".
  - **Pre-select from the filtered product list**: `loadProducts()` filters out products with no stock but
    pre-selects `products[0]` from the unfiltered list, so an out-of-stock product can be pre-selected.
  - **Catch and show errors in `mountStripeCard` and "Pay now"**: a failed Stripe.js load or mount is an
    unhandled rejection in a `setTimeout` (blank card area, nothing shown); "Pay now" returns silently when
    the element is not mounted and leaves its spinner on when Stripe is null. Show the error, allow a retry,
    and mount when the dialog is shown instead of after a fixed 300 ms.
  - **One PaymentIntent per purchase**: a retried "Buy now" creates another intent (two intents for order 66).
    Reuse the open intent for the same purchase, or cancel the abandoned one.
  - **Playwright specs for all of these**, including "typed 8 with 7 in stock, payment amount equals what is
    shown".
- **`ng test` fails on main**: `src/app/app.spec.ts` (Angular CLI scaffold) has no provider for
  `ConfirmationService`. CI only runs `ng build`, so nothing notices. Fix or delete the spec, then run
  `ng test` in CI.
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
