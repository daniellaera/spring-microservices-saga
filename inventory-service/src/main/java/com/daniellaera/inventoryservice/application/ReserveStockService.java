package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.model.ProductLockTimeoutException;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.model.UnknownProductException;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockUseCase;
import com.daniellaera.inventoryservice.domain.port.out.InventoryResultPublisher;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Deliberately not transactional: the redelivery check runs before the transaction, and the inventory result
 * is published only after StockReserver has committed or, for an unknown product, rolled back.
 * <p>
 * A product another stock change keeps locked past the lock timeout rolls the reservation back; it is tried again,
 * up to lockAttempts times in all, and then the order is REJECTED. The listener swallows exceptions, so letting
 * the timeout escape would leave the order PENDING for good, its payment captured; a rejection cancels it
 * through the saga, and payment-service refunds it.
 */
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockService.class);

    private final StockReservationRepository stockReservationRepository;
    private final StockReserver stockReserver;
    private final InventoryResultPublisher inventoryResultPublisher;
    private final int lockAttempts;

    public ReserveStockService(StockReservationRepository stockReservationRepository, StockReserver stockReserver,
                               InventoryResultPublisher inventoryResultPublisher, int lockAttempts) {
        if (lockAttempts < 1) {
            throw new IllegalArgumentException("lockAttempts must be at least 1, was " + lockAttempts);
        }
        this.stockReservationRepository = stockReservationRepository;
        this.stockReserver = stockReserver;
        this.inventoryResultPublisher = inventoryResultPublisher;
        this.lockAttempts = lockAttempts;
    }

    @Override
    public Optional<ReservationDecision> reserve(ReserveStockCommand command) {
        if (stockReservationRepository.existsByOrderId(command.orderId())) {
            log.warn("=== Inventory: stock already reserved for orderId {} — redelivery, skipping",
                    command.orderId());
            return Optional.empty();
        }

        ReservationDecision decision;
        try {
            decision = reserveWithinLockAttempts(command);
        } catch (UnknownProductException e) {
            // the reservation rolled back; rejecting lets the saga cancel the order instead of leaving it PENDING
            log.warn("=== Inventory: {} in orderId {} — REJECTING order", e.getMessage(), command.orderId());
            decision = ReservationDecision.REJECTED;
        }
        inventoryResultPublisher.publish(command, decision);
        return Optional.of(decision);
    }

    private ReservationDecision reserveWithinLockAttempts(ReserveStockCommand command) {
        for (int attempt = 1; ; attempt++) {
            try {
                return stockReserver.reserve(command);
            } catch (ProductLockTimeoutException e) {
                if (attempt == lockAttempts) {
                    log.error("=== Inventory: {} — gave up on orderId {} after {} attempt(s), REJECTING order",
                            e.getMessage(), command.orderId(), attempt);
                    return ReservationDecision.REJECTED;
                }
                log.warn("=== Inventory: {} — orderId {} rolled back, attempt {} of {}", e.getMessage(),
                        command.orderId(), attempt, lockAttempts);
            }
        }
    }
}
