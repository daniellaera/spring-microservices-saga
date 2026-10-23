# 1. Real-time order updates via SSE, not polling

Date: 2026-06-25

## Status

Accepted

## Context

Orders flow through an asynchronous Kafka saga that can take several seconds to complete. Users need to see
their order status change without refreshing the page.

## Decision

Use Server-Sent Events (SSE) from `order-service`, rather than client-side polling or WebSocket.

- The browser opens one persistent `GET /orders/stream` connection after login.
- `order-service` holds one `SseEmitter` per authenticated user in `SseEmitterRegistry`.
- When the payment result updates an order, the emitter for that user is looked up and the event
  `order-update` (`{orderId, status, userEmail}`) is pushed.
- The client updates the order row as soon as the event arrives.

**Why SSE over WebSocket?** Order updates only go from server to client; there is no need for bidirectional
messaging. SSE is plain HTTP, reconnects by itself, needs no upgrade handshake and no extra frontend library.

**Why SSE over polling?** Polling adds database load and latency. SSE delivers the event as soon as it exists.

## Consequences

- The registry keeps one emitter per user: a reconnect replaces the previous one instead of accumulating dead
  connections (fixed on 2026-07-12).
- nginx must not buffer the stream: the `/orders` location sets `proxy_buffering off` and a one-hour
  `proxy_read_timeout`. A closed stream shows up as a `499` in the nginx access log when the user leaves the
  page, which is normal.
- SSE is best effort. The dashboard also polls `GET /orders/{id}` for a new order until it is final, so a
  missed event (mobile network, reconnect) still ends in the right status.
- The emitters live in one `order-service` instance. Running several instances would need a shared fan-out
  (for example, a Kafka consumer per instance).
