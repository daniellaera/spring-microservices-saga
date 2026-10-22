package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.in.CompensateStockUseCase;
import com.daniellaera.inventoryservice.domain.port.in.PaymentOutcome;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;
import com.daniellaera.inventoryservice.domain.port.out.CompensationLedger;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Not transactional (current behaviour): every save commits on its own and the ledger entry is written last,
 * so a crash mid-way leaves a partial restore that a redelivery restores again (see the backlog).
 */
public class CompensateStockService implements CompensateStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompensateStockService.class);

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final CompensationLedger compensationLedger;
    private final AuditTrail auditTrail;

    public CompensateStockService(ProductRepository productRepository,
                                  StockReservationRepository stockReservationRepository,
                                  CompensationLedger compensationLedger, AuditTrail auditTrail) {
        this.productRepository = productRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.compensationLedger = compensationLedger;
        this.auditTrail = auditTrail;
    }

    @Override
    public void compensate(PaymentOutcome outcome) {
        log.info("=== Received payment result for orderId: {} status: {}", outcome.orderId(), outcome.status());

        if (!outcome.failed()) {
            log.info("=== Payment SUCCESS — no compensation needed for orderId: {}", outcome.orderId());
            return;
        }

        if (compensationLedger.isCompensated(outcome.orderId())) {
            log.warn("=== Compensation already applied for orderId: {} — skipping", outcome.orderId());
            return;
        }

        // release only what was actually reserved for this order — a FAILED payment that follows a
        // REJECTED inventory result has no reservation and must not touch stock
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(outcome.orderId());
        if (reservations.isEmpty()) {
            log.warn("=== Payment FAILED — no stock reservation recorded for orderId {} — nothing to release",
                    outcome.orderId());
            return;
        }
        log.info("=== Payment FAILED — compensating {} reserved item(s) for orderId {}",
                reservations.size(), outcome.orderId());

        int releasedQuantity = 0;
        for (StockReservation reservation : reservations) {
            Product product = productRepository.findByName(reservation.productName()).orElse(null);
            if (product == null) {
                log.error("=== Compensation FAILED — product {} not found", reservation.productName());
                continue;
            }
            product.release(reservation.quantity());
            productRepository.save(product);
            releasedQuantity += reservation.quantity();
            log.info("=== Compensation SUCCESS — {} stock restored to {}", reservation.productName(), product.quantity());
            auditTrail.stockRestored(product, reservation, outcome.userEmail());
        }

        compensationLedger.record(outcome.orderId(), reservations.get(0).productName(), releasedQuantity);
    }
}
