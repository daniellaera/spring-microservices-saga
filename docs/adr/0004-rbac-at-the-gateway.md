# 4. RBAC at the gateway layer

Date: 2026-06-25

## Status

Accepted

## Context

Role-based access control must be enforced consistently. Implementing it in every microservice duplicates it
and risks inconsistencies.

## Decision

All RBAC enforcement happens in the gateway's `JwtWebFilter`. Downstream services receive `X-User-Email` and
`X-User-Role` as trusted headers; they never inspect the JWT.

**Why not in each service?** The gateway is the single entry point for HTTP traffic. With RBAC there,
downstream services hold only business logic, and a new rule is one change in one place.

**Why not a separate auth middleware service?** The gateway already handles routing, circuit breaking and rate
limiting. A dedicated middleware would add a network hop to every request.

## Consequences

- The services trust the headers, so they must only be reachable through the gateway. Inside the Docker
  network anyone can call `order-service` with any `X-User-Email`; the end-to-end check of the 2026-10-23
  maintenance window placed its test order that way, on purpose.
- Only `/auth/**` and `/actuator/**` are public. The gateway's own `/fallback/*` handlers also need a valid
  token, so a fallback can only be observed through a logged-in request.
- ADMIN-only routes (product create and restock, `/audit`, the all-orders list) are listed in one filter.
