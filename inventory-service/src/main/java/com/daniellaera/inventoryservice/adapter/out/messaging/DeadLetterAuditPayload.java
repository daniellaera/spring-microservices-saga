package com.daniellaera.inventoryservice.adapter.out.messaging;

/** STOCK_RESERVATION_DEAD_LETTERED / STOCK_RESTORE_DEAD_LETTERED audit payload: which order, and why it gave up. */
record DeadLetterAuditPayload(Long orderId, String error) {
}
