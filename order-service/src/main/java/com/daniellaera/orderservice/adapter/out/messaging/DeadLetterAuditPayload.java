package com.daniellaera.orderservice.adapter.out.messaging;

/** ORDER_DEAD_LETTERED audit payload: which order is stuck, and why its payment result gave up. */
record DeadLetterAuditPayload(Long orderId, String error) {
}
