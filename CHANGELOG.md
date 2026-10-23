# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-10-23

Hardening of the order saga: payments, stock and order events are now idempotent and safe to redeliver, a
failed order refunds its payment, and the CI runs (and gates) every test suite. Pull request numbers refer to
the Gitea repository `spring-saga-store`.

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

- **`.dockerignore`** (#8): local configuration (`*-local.yml`, which can hold credentials) and `.env` files
  no longer reach the build context or the images.

## 1.0-SNAPSHOT (never tagged)

The state of `main` up to `d5fa6b7` (2026-07-21): the microservices, the choreographed saga, Stripe test-mode
payments, SSE updates, the audit trail, 2FA by email OTP, and Gitea Actions CI/CD to infra-node1. No release
was tagged; see the git history.

[Unreleased]: https://github.com/daniellaera/spring-microservices-saga/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/daniellaera/spring-microservices-saga/releases/tag/v1.1.0
