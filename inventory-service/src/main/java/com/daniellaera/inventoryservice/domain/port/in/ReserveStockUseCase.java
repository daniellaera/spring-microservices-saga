package com.daniellaera.inventoryservice.domain.port.in;

import com.daniellaera.inventoryservice.domain.model.ReservationDecision;

import java.util.Optional;

public interface ReserveStockUseCase {
    /** Empty when stock was already reserved for the order (redelivery): nothing is done or published. */
    Optional<ReservationDecision> reserve(ReserveStockCommand command);
}
