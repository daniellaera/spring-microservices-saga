package com.daniellaera.paymentservice.adapter.out.messaging;

/** PAYMENT_DEAD_LETTERED audit payload: which order is stuck, and why its inventory result gave up. */
record DeadLetterAuditPayload(Long orderId, String error) {
}
