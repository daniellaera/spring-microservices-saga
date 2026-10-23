# 8. Refresh token rotation

Date: 2026-07-15

## Status

Accepted. Restated with its security properties in [ADR 12](0012-refresh-token-rotation-restated.md).

## Context

Access tokens live 15 minutes. Without a refresh mechanism, users must log in again every 15 minutes.

## Decision

Issue 7-day refresh tokens, stored in PostgreSQL and rotated on every use. The Angular interceptor refreshes
transparently on a 401, and a timer refreshes before the session expires.

**Why rotation?** Each refresh issues a new token and revokes the old one. A stolen refresh token stops working
after the legitimate user's next refresh.

## Consequences

- A refresh is a database write in `auth-service`.
- Logout revokes the refresh token server-side.
