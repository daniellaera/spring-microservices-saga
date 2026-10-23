# 12. Refresh token rotation (restated)

Date: 2026-07-16

## Status

Accepted. Restates [ADR 8](0008-refresh-token-rotation.md): `docs/ARCHITECTURE.md` held both entries, and both
are kept so the numbering stays stable.

## Context

15-minute access tokens need silent renewal, without forcing users to log in again.

## Decision

7-day refresh tokens stored in PostgreSQL, rotated on every use. The Angular interceptor refreshes
transparently on a 401 and retries the original request.

## Consequences

Security properties:

- a stolen refresh token is invalidated after the first legitimate use;
- logout revokes the token on the server;
- rotation makes theft attempts detectable;
- the short access-token lifetime limits exposure.
