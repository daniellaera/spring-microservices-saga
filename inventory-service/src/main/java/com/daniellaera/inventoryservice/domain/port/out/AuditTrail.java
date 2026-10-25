package com.daniellaera.inventoryservice.domain.port.out;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;

/**
 * Never throws. When called inside a transaction, the event is only published after it commits.
 */
public interface AuditTrail {
    void stockDeducted(Product product, OrderLine line, Long orderId, String userEmail);

    void stockRestored(Product product, StockReservation reservation, String userEmail);

    void productCreated(Product product);

    void productRestocked(Product product);

    // a dead-lettered order (no stock reserved) or payment result (stock not released); orderId null if unreadable
    void stockReservationDeadLettered(Long orderId, String userEmail, String error);

    void stockRestoreDeadLettered(Long orderId, String userEmail, String error);
}
