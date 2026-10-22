package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.model.UnknownProductException;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The transactional part of a reservation: product resolution, stock check, reservation inserts and
 * deductions either all commit or all roll back. A separate bean from ReserveStockService so that the
 * transactional proxy applies (no self-invocation).
 */
public class StockReserver {

    private static final Logger log = LoggerFactory.getLogger(StockReserver.class);

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final AuditTrail auditTrail;

    public StockReserver(ProductRepository productRepository, StockReservationRepository stockReservationRepository,
                         AuditTrail auditTrail) {
        this.productRepository = productRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public ReservationDecision reserve(ReserveStockCommand command) {
        List<OrderLine> lines = command.lines();
        log.info("=== Inventory: received order {} with {} item(s)", command.orderId(), lines.size());

        // resolve products up front: an unknown product aborts the whole order (rollback, nothing published).
        // One instance per name, as the persistence context gave the same entity for a repeated product.
        Map<String, Product> productsByName = new LinkedHashMap<>();
        for (OrderLine line : lines) {
            if (!productsByName.containsKey(line.productName())) {
                productsByName.put(line.productName(), productRepository.findByName(line.productName())
                        .orElseThrow(() -> new UnknownProductException(line.productName())));
            }
        }

        boolean sufficientStock = lines.stream()
                .allMatch(line -> productsByName.get(line.productName()).canReserve(line.quantity()));
        if (!sufficientStock) {
            log.warn("=== Inventory: insufficient stock for orderId {} — REJECTING order", command.orderId());
            return ReservationDecision.REJECTED;
        }

        for (OrderLine line : lines) {
            Product product = productsByName.get(line.productName());
            // reservation goes in before the deduction: the unique constraint on
            // (order_id, product_name) is the last-line idempotency guard
            stockReservationRepository.save(StockReservation.of(command.orderId(), line.productName(), line.quantity()));
            product.deduct(line.quantity());
            productRepository.save(product);
            log.info("=== Inventory: deducted {} x{} — {} remaining",
                    line.productName(), line.quantity(), product.quantity());
            // published only once the transaction has committed
            auditTrail.stockDeducted(product, line, command.userEmail());
        }
        log.info("=== Inventory: stock reserved for orderId {}", command.orderId());
        return ReservationDecision.APPROVED;
    }
}
