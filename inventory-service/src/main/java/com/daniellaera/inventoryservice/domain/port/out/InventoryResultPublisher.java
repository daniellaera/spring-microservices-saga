package com.daniellaera.inventoryservice.domain.port.out;

import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;

public interface InventoryResultPublisher {
    /** Never throws: a failed publish is logged and swallowed. */
    void publish(ReserveStockCommand command, ReservationDecision decision);
}
