package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
import com.daniellaera.inventoryservice.domain.port.out.InventoryResultPublisher;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Deliberately not transactional: the redelivery check runs before the transaction, and the inventory result
 * is published only after StockReserver has committed.
 */
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockService.class);

    private final StockReservationRepository stockReservationRepository;
    private final StockReserver stockReserver;
    private final InventoryResultPublisher inventoryResultPublisher;

    public ReserveStockService(StockReservationRepository stockReservationRepository, StockReserver stockReserver,
                               InventoryResultPublisher inventoryResultPublisher) {
        this.stockReservationRepository = stockReservationRepository;
        this.stockReserver = stockReserver;
        this.inventoryResultPublisher = inventoryResultPublisher;
    }

    @Override
    public Optional<ReservationDecision> reserve(ReserveStockCommand command) {
        if (stockReservationRepository.existsByOrderId(command.orderId())) {
            log.warn("=== Inventory: stock already reserved for orderId {} — redelivery, skipping",
                    command.orderId());
            return Optional.empty();
        }

        ReservationDecision decision = stockReserver.reserve(command);
        inventoryResultPublisher.publish(command, decision);
        return Optional.of(decision);
    }
}
