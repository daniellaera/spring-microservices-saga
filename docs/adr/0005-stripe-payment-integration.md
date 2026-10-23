# 5. Stripe payment integration

Date: 2026-06-25

## Status

Accepted. Compensation of a captured payment is added by [ADR 17](0017-payment-compensation-by-refund.md).

## Context

Payment processing has to be real, testable with test cards, and safe: the application must never store raw
card data.

## Decision

Use Stripe in test mode with the PaymentIntent API. The UI embeds Stripe Elements, which collects the card on
Stripe's infrastructure, so PCI scope never reaches this application.

1. The user checks out, and the frontend calls `POST /payments/create-intent`. `payment-service` creates a
   PaymentIntent through the Stripe API and returns its `clientSecret`.
2. The user submits the card through Stripe Elements, and Stripe confirms the payment in the browser.
3. The frontend calls `POST /orders` with the `paymentIntentId`.
4. `order-service` starts the saga. When inventory approves, `payment-service` retrieves the PaymentIntent from
   Stripe and checks it.
5. On success the order is CONFIRMED; otherwise it fails and compensation runs.

**Why payment before order?** No order row exists for a purchase that was never paid.

**Why verify the PaymentIntent server-side?** The browser's confirmation can be forged. Retrieving the intent
from Stripe in `payment-service` makes Stripe's answer authoritative.

## Consequences

- The money is captured in the browser, before the saga runs. When the saga then fails (no stock, wrong
  amount), the customer has paid for an order that is cancelled. Until 1.1.0 nothing gave that money back:
  see [ADR 17](0017-payment-compensation-by-refund.md).
- "Verified" had to become stricter over time (1.1.0): the intent must have succeeded for exactly the order's
  total in the shop currency, an order without an intent fails, and an intent pays for one order at most.
- The browser decides when to create an intent. Each retried "Buy now" used to create a new one, leaving
  unused intents in Stripe; since 1.1.0 the checkout keeps one intent per purchase.
