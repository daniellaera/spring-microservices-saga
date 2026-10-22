package com.daniellaera.inventoryservice.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stock actually deducted for an order item when inventory APPROVED the order. Compensation releases only what
 * is recorded here, so a FAILED payment for an order that was never reserved (e.g. REJECTED) releases nothing.
 */
public record StockReservation(UUID id, Long orderId, String productName, int quantity, LocalDateTime reservedAt) {

    /** A reservation that has never been persisted: persistence assigns the id and reservedAt. */
    public static StockReservation of(Long orderId, String productName, int quantity) {
        return new StockReservation(null, orderId, productName, quantity, null);
    }
}
