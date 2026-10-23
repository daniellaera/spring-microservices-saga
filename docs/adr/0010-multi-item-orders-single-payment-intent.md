# 10. Multi-item orders with a single PaymentIntent

Date: 2026-07-15

## Status

Accepted

## Context

Cart checkout created one order per item: several Stripe charges, several emails, and a confusing experience.

## Decision

One order with an `order_items` table. One Stripe PaymentIntent for the cart total. One email with an itemized
summary. Inventory checks every item before deducting any stock.

**Why all-or-nothing inventory?** It prevents partial fulfillment: either every item is available, or the whole
order is rejected.

## Consequences

- Events carry an `items` list next to legacy single-item fields (`productName`, `quantity`, `price`), which
  orders created before `order_items` still need.
- The payment must cover exactly the order's total; since 1.1.0 payment-service checks the amount and the
  currency of the intent.
- Reserving several products at once needs a lock order: since 1.1.0 inventory-service locks the products of
  an order in name order, so two orders can't deadlock.
