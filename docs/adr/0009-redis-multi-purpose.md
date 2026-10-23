# 9. Redis multi-purpose strategy

Date: 2026-07-15

## Status

Accepted

## Context

Redis was already required for the gateway's rate limiting. Cart storage and caching needs came up separately.

## Decision

One Redis instance serves three uses, rather than separate infrastructure per service:

- gateway: `RequestRateLimiter` per user or IP;
- `cart-service`: one hash per user, with a 30-minute TTL;
- `inventory-service`: the product list cache (`@Cacheable` / `@CacheEvict`, cache `products-v2`).

## Consequences

- One instance to run, and a single point of failure for rate limiting, carts and the product cache.
- A cache must be evicted on every write that changes what it shows. The product list cache was evicted on
  create and restock only, so the dropdown showed the stock from before an order until the entry expired.
  Since 1.1.0 inventory-service also evicts it after a reservation or a compensation commits.
- OTP codes (`auth-service`, 2FA) also live in Redis with a TTL.
