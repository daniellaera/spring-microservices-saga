package com.daniellaera.paymentservice.adapter.out.messaging;

/** PAYMENT_REFUND_FAILED audit payload: what needs a manual refund, and Stripe's error code. */
record RefundFailedAuditPayload(Long orderId, String paymentIntentId, String errorCode) {
}
