# 3. Centralized configuration via Spring Cloud Config

Date: 2026-06-25

## Status

Accepted

## Context

Each service has its own configuration. Managing every service's YAML separately leads to drift, and makes
cross-cutting changes (for example the OTLP endpoint) error-prone.

## Decision

Run a dedicated `config-server` that serves the configuration of every service from
`config-server/src/main/resources/configs/`. Services fetch their configuration at startup through the Spring
Cloud Config client.

**Why Spring Cloud Config?** One change in `configs/application.yml` reaches every service on restart, and
environment profiles are managed in one place.

## Consequences

- A service cannot start without the config server: `spring.config.import: configserver:` is not optional.
  The saga e2e suite therefore runs a WireMock config server that serves an empty environment.
  Since 2026-10-25 a client waits for a config server that is still starting: `fail-fast` with spring-retry,
  10 attempts over about 85 s (3 s connect, 10 s read timeout each), then the startup fails with
  `ConfigClientFailFastException`. `ClientConfigImportTest` (config-server module) checks every client's own
  `application.yml`.
- A reachable config server with no file for the service is not detected: the native backend still answers
  with the shared `configs/application.yml`, and the service starts on its local defaults (BACKLOG.md).
- A configuration change takes effect when the service that reads it restarts. The deploy only restarts the
  services whose directory changed: a change to `configs/gateway-service.yml` alone restarts `config-server`,
  not the gateway. When both change, the gateway waits for `config-server: service_healthy` and reads the new
  file.
- Operational settings live there too. The gateway's circuit-breaker time limits (`gateway-service.yml`) were
  missing until 2026-10-23, so resilience4j's default of 1 s applied to `/payments` (see the CHANGELOG, 1.1.0).
- `*-local.yml` files can hold local credentials; `.dockerignore` keeps them out of the image.
