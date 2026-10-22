package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.StockReservation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * STOCK_RESTORED audit payload. It used to be the JPA entity serialized as-is, which Jackson writes with its
 * properties in alphabetical order; the components are declared in that order to keep the JSON identical.
 */
record StockReservationAuditPayload(UUID id, Long orderId, String productName, Integer quantity,
                                    LocalDateTime reservedAt) {

    static StockReservationAuditPayload of(StockReservation reservation) {
        return new StockReservationAuditPayload(reservation.id(), reservation.orderId(), reservation.productName(),
                reservation.quantity(), reservation.reservedAt());
    }
}
