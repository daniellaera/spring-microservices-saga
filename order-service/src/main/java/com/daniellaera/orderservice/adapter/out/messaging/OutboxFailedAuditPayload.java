package com.daniellaera.orderservice.adapter.out.messaging;

/** OUTBOX_EVENT_FAILED audit payload: which event of the order was given up on, after how many attempts, and why. */
record OutboxFailedAuditPayload(long orderId, String eventType, int attempts, String error) {
}
