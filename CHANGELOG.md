# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Per-order saga timeline** ([ADR 18](docs/adr/0018-saga-timeline-correlation-id.md)). The saga audit events
  of order-, inventory- and payment-service carry a new `correlationId` (the order id); audit-trail-service
  stores it (Flyway V2, nullable, no backfill) and answers `GET /audit/orders/{id}/timeline`, older events
  matched by their entity. shop-ui shows it at `/audit/orders/:orderId`, linked from the audit trail and, for
  admins, from the dashboard's orders: outcome, compensation, and each step's time in the browser's zone.
- **Server-side pagination and filters on `GET /audit`**: `q`, `eventType`, `serviceName`, `userEmail`,
  `orderId`, `from`, `to`, newest first, at most 200 per page. The audit page uses them instead of searching
  the 200 newest events in the browser.
- Audit events have `recordedAt`, their recording instant in UTC with its offset; `createdAt` is unchanged.
- **Stripe warm-up in payment-service**: once the service is ready, one authenticated
  `GET /v1/payment_intents?limit=1` on its own thread (5 s connect, 10 s read timeout), so the first buyer
  after a restart no longer waits for the SDK's first call (over 1 s on 2026-10-23). A failure is only logged;
  without a secret key it is skipped.

### Changed

- **Service images are runtime-only** (#31): `eclipse-temurin:21-jre-jammy` plus the service's jar, which
  `main.yml` builds for every changed service in one Maven run (`-pl <services> -am`) instead of each image
  building the whole reactor. Images 486–568 MB → 330–412 MB; a full rebuild's image job 276–286 s → 226 s.
  config-server's jar never contains `*-local.yml`.

### Fixed

- **`/orders/stream` while order-service is down** answers 503 (`/fallback/order-stream`, JSON) instead of a
  500 with a stack trace in the gateway log. Its own breaker `order-stream-cb` has no time limit, so a long-lived
  stream is never cut; shop-ui reconnects after the 503 as after any non-200 answer.
- **Retries and dead-letter handling in inventory-service and order-service.** Their listeners swallowed every
  failure (a database blip left the order PENDING), or retried it 10 times in a row and dropped it
  (compensation), while their `@DltHandler(String, Exception)` methods never ran. Each listener now declares
  `@RetryableTopic` like payment-service: 5 attempts after 2, 6, 18 and 54 s on the service's own retry topics,
  an unreadable event straight to the dead-letter topic, whose handler reads the `kafka_exception-message`
  header, logs the record once and never throws.
- **A config server still starting no longer fails the other services' startup.** Every client of
  config-server sets `spring.cloud.config.fail-fast` with spring-retry: 10 attempts over about 85 s
  (3 s connect and 10 s read timeout each), then a clear `ConfigClientFailFastException`. The import stays
  mandatory. `ClientConfigImportTest` runs each client's `application.yml` against a stub config server.
- `GET /audit/user/{email}` and `GET /audit/order/{orderId}` answered 500 (unnamed path variables).
- **`main.yml` deployed after failed tests** (run 259): with the images not built, the deploy restarted the
  changed services on their previous `:latest` and logged a SHA tag that was never pushed. The deploy now
  needs the test jobs. `workflow_dispatch` takes an optional `base` commit, so a failed run can be redeployed
  with every service changed since the last good one.

## [1.1.0] - 2026-10-23

Hardening of the order saga: payments, stock and order events are now idempotent and safe to redeliver, a
failed order refunds its payment, and the CI runs (and gates) every test suite. Pull request numbers refer to
the Gitea repository `spring-saga-store` (#1–#24; #18 was closed unmerged, superseded by #21).

### Added

- **Payment compensation by refund** (#17). A failed order whose PaymentIntent was captured, and paid no other
  order, is refunded once (`Idempotency-Key: refund-order-<id>`). The refund id is stored in
  `transaction.refund_id` (Flyway V5), and audited as `PAYMENT_REFUNDED`. A refusal from Stripe is audited as
  `PAYMENT_REFUND_FAILED` for a manual refund. See [ADR 17](docs/adr/0017-payment-compensation-by-refund.md).
- **Dead-letter handling in payment-service** (#17): after the last retry, an inventory result is logged once
  and audited as `PAYMENT_DEAD_LETTERED`; runbook `docs/maintenance/replay-dead-lettered-inventory-results.md`.
- **Saga end-to-end suite** (`saga-e2e`, Cucumber, #10): the three saga services from their jars on real
  Kafka, PostgreSQL and Redis, Stripe stubbed by WireMock. 13 scenarios, run by `e2e.yml` (#10, #12).
- **Post-deploy smoke checks** in `main.yml` (#11): health of every redeployed container, and the public
  site (`/` 200, `/products` 401).
- **Image tags per commit** (#7): every image is pushed as `:latest` and `:<short sha>`; rollback procedure in
  `docs/ROLLBACK.md`.
- **Docker build check** workflow (#8): builds all images on pull requests that touch a Dockerfile or
  `.dockerignore`.
- **shop-ui tests in CI** (#14, #21): Playwright 1.64.0 specs (audit page, checkout, order form, public pages)
  and `ng test` run on every pull request that touches shop-ui. The `@public` specs need no login or payment
  and are safe against production.
- **Outbox dead-letter** (#24): each outbox row has `attempts`, `status` (PENDING, PUBLISHED, FAILED) and
  `last_error` (order-service Flyway V7). A row that can never be sent (unreadable payload, or a record Kafka
  refuses as such) is FAILED after `outbox.relay.max-attempts` (5) and audited as `OUTBOX_EVENT_FAILED`.
  Transient failures never count.
- **Audit event types** `PAYMENT_REFUNDED`, `PAYMENT_REFUND_FAILED`, `PAYMENT_DEAD_LETTERED` and
  `OUTBOX_EVENT_FAILED` in audit-trail-service, with their badges on the admin `/audit` page (#24).
  `AuditEventTypeTest` fails the build when a producer sends a type the enum doesn't know.
- **Checkout**: "Only N left" when the quantity exceeds the stock (#21).
- **Gateway**: a `/fallback/payments` handler answering 503 (#19).
- **Documentation**: architecture decision records in `docs/adr` (ADRs 1–12 migrated, 13–17 new), README
  diagrams (architecture, saga with compensation, hexagonal layout), how to run the tests, live badges, this
  changelog. Runbooks in `docs/maintenance/`. "Accounts and access" in the README (#6).

### Changed

- **Hexagonal architecture** (ports and adapters) for order-service (#3), inventory-service (#5) and
  payment-service (#9), enforced by ArchUnit; observable behaviour unchanged. See
  [ADR 15](docs/adr/0015-hexagonal-architecture.md).
- **Coverage gate** raised from 85 % to 90 % aggregate instruction coverage (#4).
- **Outbox delivery is at least once** (#16): the relay waits for Kafka's acknowledgement (up to
  `outbox.relay.send-timeout`, 5 s), marks a row published only then, and stops the batch on a failed send. See
  [ADR 16](docs/adr/0016-transactional-outbox-at-least-once.md).
- **Retries of an inventory result** in payment-service are declared with `@RetryableTopic` (#17): 5 attempts,
  after 2, 6, 18 and 54 s, on `inventory-topic-retry-0..3`, then `inventory-topic-dlt`. They don't block other
  orders. Before: Spring Boot's `spring.kafka.retry.topic.*`, 3 attempts in about 3 s.
- **Transient Stripe errors** (no answer, 429, 5xx) are retried instead of failing the order (#17).
- **Gateway circuit-breaker time limits** are explicit (#19): `payment-cb` 10 s, the other breakers 1 s.
- **`PRODUCT_RESTOCKED`** is audited after the restock commits, not before (#15).
- **Product locks give up after 3 s** (#24): inventory-service sets `lock_timeout` 3000 ms (`SET LOCAL`)
  before each `FOR UPDATE` finder, because Hibernate 7.2.1 applies the JPA lock-timeout hint too late. A
  reservation is retried up to `inventory.reservation.lock-attempts` (3) times, then the order is REJECTED
  (and its payment refunded); a compensation propagates and is redelivered; a restock answers 503 with
  `Retry-After: 1`.
- **Error responses of auth-service** carry a `message` field, which the login page shows (#23).
- **Kafka and Grafana keep their data on named volumes** (`kafka-data`, `grafana-data`), and the Grafana image
  is pinned by digest (#20). Executed in the 2026-10-23 maintenance window
  (`docs/maintenance/kafka-and-grafana-volumes.md`).
- **The deploy job no longer runs `docker container prune`** (#20): it deleted containers stopped on purpose
  for maintenance.
- **The shop-ui version badge** gets the commit SHA from a Docker build argument (`BUILD_SHA`), instead of a
  `sed` in `main.yml` (#21).
- **The demo login credentials** show only in development builds (#6).
- Version 1.1.0 in every POM and in shop-ui's `package.json`.

### Fixed

- **CI ran no tests** (#2): Maven 3.8.7 bound a surefire that skipped JUnit 5, so every module reported 0
  tests. Plugins are pinned, Maven ≥ 3.9.6 and Java ≥ 21 are enforced, and the CI image is
  `ci-java21-node24:bookworm-2`.
- **Phantom stock** (2026-10-21, `5646245`): compensation released the requested quantities even when nothing
  had been reserved. It now releases only what `stock_reservation` recorded.
- **Payment integrity** (#13):
  - a redelivered inventory result was paid and recorded twice. Now one transaction per order (unique
    constraint, V3), and a redelivery republishes the stored outcome;
  - an order without a payment intent was confirmed for free; it now fails;
  - the intent's amount and currency were not checked; they must now match the order;
  - one intent could pay several orders; now it pays one order at most (V4);
  - a late or duplicate payment result could change a final order; the status now changes only from PENDING.
- **Inventory integrity** (#15):
  - compensation released stock row by row; it is now one transaction with its log entry;
  - concurrent reservations, compensations and restocks lost updates (10 orders of 1 against a stock of 5 were
    all approved). Products are locked (`SELECT … FOR UPDATE`) in name order;
  - an order for an unknown product stayed PENDING forever; it is now REJECTED;
  - the cached product list showed the stock from before an order or a compensation.
- **Lost order events** (#16): a failed send to Kafka was swallowed and the outbox row marked published.
- **The payment compensation audit events were dropped** (#24): PR #17 published `PAYMENT_REFUNDED`,
  `PAYMENT_REFUND_FAILED` and `PAYMENT_DEAD_LETTERED`, but audit-trail-service stores only the types of its
  `AuditEventType` enum and dropped the others. None had been emitted in production yet.
- **A stuck product lock blocked every order of that product forever** (#24): `SELECT … FOR UPDATE` waited
  without a limit.
- **An outbox row that can never be sent** was skipped on every tick, forever, with only an ERROR log (#24).
- **The dead-letter handler never ran** (#17): Spring Boot's retry-topic configuration wired Spring Kafka's
  default handler, which failed on every record under `ack-mode: record` and looped (about 20,000 stack traces
  a minute).
- **Admin audit page** (#14): reloading a page whose path is also an API path (`/audit`, `/products`) returned
  an empty 401; the search ignored the entity id; rows never expanded; the Entity and Service columns were
  always hidden.
- **First "Buy now" after a payment-service restart failed with a 404** (#19): the gateway's default 1 s time
  limit tripped while Stripe answered slowly, and the payments fallback did not exist.
- **Checkout** (#21):
  - a quantity above the stock was silently clamped to the stock when "Buy now" was clicked;
  - an out-of-stock product could be pre-selected;
  - every retried "Buy now" created a new PaymentIntent;
  - the card form could mount before the dialog was ready, and its errors and "Pay now"'s were never shown;
  - the form kept stale state between purchases;
  - `app.spec.ts` failed, unnoticed because CI didn't run `ng test`.
- **The login page showed the wrong demo password** (#6).

### Security

- **Demo accounts removed** (#23): `V3__remove_demo_users.sql` deletes `admin@example.com` and
  `daniel@example.com`, and their refresh tokens, in every environment. The demo seed is now a repeatable
  migration in `db/dev` (`R__demo_users.sql`) that only the `local` profile enables, so local development still
  has both accounts.
- **OTP verification is limited** (#23): the 5th wrong code invalidates the OTP (429), and at most 5 OTPs are
  issued per email in 15 minutes (429), counted after the password check. Both are audited as
  `USER_LOGIN_FAILED`. Before, a wrong code left the OTP valid for its full 5 minutes, with no limit.
- **`.dockerignore`** (#8): local configuration (`*-local.yml`, which can hold credentials) and `.env` files
  no longer reach the build context or the images.

## 1.0-SNAPSHOT (never tagged)

The state of `main` up to `d5fa6b7` (2026-07-21): the microservices, the choreographed saga, Stripe test-mode
payments, SSE updates, the audit trail, 2FA by email OTP, and Gitea Actions CI/CD to infra-node1. No release
was tagged; see the git history.

[Unreleased]: https://github.com/daniellaera/spring-microservices-saga/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/daniellaera/spring-microservices-saga/releases/tag/v1.1.0
