package com.daniellaera.paymentservice.adapter.out.messaging;

/** PAYMENT_REFUNDED audit payload: the failed order, the intent whose payment was given back, and the refund. */
record RefundAuditPayload(Long orderId, String paymentIntentId, String refundId) {
}
