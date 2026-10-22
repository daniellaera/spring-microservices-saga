package com.daniellaera.inventoryservice.domain.port.out;

import com.daniellaera.inventoryservice.domain.model.StockReservation;

import java.util.List;

public interface StockReservationRepository {
    /** Inserts a new reservation; (orderId, productName) is unique. */
    StockReservation save(StockReservation reservation);

    List<StockReservation> findByOrderId(long orderId);

    boolean existsByOrderId(long orderId);
}
