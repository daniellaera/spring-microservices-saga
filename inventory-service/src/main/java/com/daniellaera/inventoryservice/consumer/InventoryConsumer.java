package com.daniellaera.inventoryservice.consumer;

import com.daniellaera.inventoryservice.audit.AuditPublisher;
import com.daniellaera.inventoryservice.dto.InventoryResultEvent;
import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.dto.OrderItemEvent;
import com.daniellaera.inventoryservice.dto.PaymentEvent;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryConsumer {

    private final ProductJpaRepository productRepository;
    private final CompensationLogJpaRepository compensationLogRepository;
    private final StockReservationJpaRepository stockReservationRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuditPublisher auditPublisher;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "orders-topic", groupId = "inventory-group")
    public void consumeOrder(String message) {
        InventoryResultEvent result;
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            if (stockReservationRepository.existsByOrderId(event.orderId())) {
                log.warn("=== Inventory: stock already reserved for orderId {} — redelivery, skipping",
                        event.orderId());
                return;
            }

            // stock check, reservation insert and deduction run in one transaction;
            // any exception rolls back the whole order
            result = transactionTemplate.execute(status -> reserveStock(event));
        } catch (Exception e) {
            log.error("=== Inventory: failed to process order event: {}", e.getMessage());
            return;
        }

        // publish only after the transaction has committed
        try {
            kafkaTemplate.send("inventory-topic", objectMapper.writeValueAsString(result));
            log.info("=== Inventory: published {} to inventory-topic for orderId {}",
                    result.status(), result.orderId());
        } catch (Exception e) {
            log.error("=== Inventory: failed to publish inventory result for orderId {}: {}",
                    result.orderId(), e.getMessage());
        }
    }

    private InventoryResultEvent reserveStock(OrderEvent event) {
        List<OrderItemEvent> items = resolveItems(event.items(), event.productName(), event.quantity(),
                event.price(), event.totalAmount());
        log.info("=== Inventory: received order {} with {} item(s)", event.orderId(), items.size());

        // resolve products up front — product-not-found aborts processing entirely (no publish)
        List<ProductJpaEntity> products = items.stream()
                .map(item -> productRepository.findByName(item.productName())
                        .orElseThrow(() -> new RuntimeException("Product not found: " + item.productName())))
                .toList();

        boolean sufficientStock = true;
        for (int i = 0; i < items.size(); i++) {
            if (products.get(i).getQuantity() < items.get(i).quantity()) {
                sufficientStock = false;
                break;
            }
        }

        String status;
        int reservedQuantity;
        if (sufficientStock) {
            for (int i = 0; i < items.size(); i++) {
                ProductJpaEntity product = products.get(i);
                OrderItemEvent item = items.get(i);
                // reservation goes in before the deduction: the unique constraint on
                // (order_id, product_name) is the last-line idempotency guard
                StockReservationJpaEntity reservation = new StockReservationJpaEntity();
                reservation.setOrderId(event.orderId());
                reservation.setProductName(item.productName());
                reservation.setQuantity(item.quantity());
                stockReservationRepository.save(reservation);
                product.setQuantity(product.getQuantity() - item.quantity());
                productRepository.save(product);
                log.info("=== Inventory: deducted {} x{} — {} remaining",
                        item.productName(), item.quantity(), product.getQuantity());
                // audit only once the transaction has committed — a rollback must not emit STOCK_DEDUCTED
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        auditPublisher.publish("STOCK_DEDUCTED", event.userEmail(), "PRODUCT",
                                String.valueOf(product.getId()), item);
                    }
                });
            }
            status = "APPROVED";
            reservedQuantity = event.quantity();
            log.info("=== Inventory: stock reserved for orderId {}", event.orderId());
        } else {
            status = "REJECTED";
            reservedQuantity = 0;
            log.warn("=== Inventory: insufficient stock for orderId {} — REJECTING order", event.orderId());
        }

        return new InventoryResultEvent(
                event.orderId(),
                status,
                event.productName(),
                reservedQuantity,
                event.price(),
                event.totalAmount(),
                event.userEmail(),
                event.paymentIntentId(),
                items
        );
    }

    private List<OrderItemEvent> resolveItems(List<OrderItemEvent> items, String productName, Integer quantity,
                                               BigDecimal price, BigDecimal totalAmount) {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        return List.of(new OrderItemEvent(productName, quantity, price, totalAmount));
    }

    @KafkaListener(topics = "payment-topic", groupId = "inventory-compensation-group")
    public void handlePaymentResult(String message) throws Exception {
        PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);

        log.info("=== Received payment result for orderId: {} status: {}", event.orderId(), event.status());

        if (!"FAILED".equals(event.status())) {
            log.info("=== Payment SUCCESS — no compensation needed for orderId: {}", event.orderId());
            return;
        }

        if (compensationLogRepository.existsByOrderId(event.orderId())) {
            log.warn("=== Compensation already applied for orderId: {} — skipping", event.orderId());
            return;
        }

        // release only what was actually reserved for this order — a FAILED payment that follows a
        // REJECTED inventory result has no reservation and must not touch stock
        List<StockReservationJpaEntity> reservations = stockReservationRepository.findByOrderId(event.orderId());
        if (reservations.isEmpty()) {
            log.warn("=== Payment FAILED — no stock reservation recorded for orderId {} — nothing to release",
                    event.orderId());
            return;
        }
        log.info("=== Payment FAILED — compensating {} reserved item(s) for orderId {}",
                reservations.size(), event.orderId());

        int releasedQuantity = 0;
        for (StockReservationJpaEntity reservation : reservations) {
            ProductJpaEntity product = productRepository.findByName(reservation.getProductName()).orElse(null);
            if (product == null) {
                log.error("=== Compensation FAILED — product {} not found", reservation.getProductName());
                continue;
            }
            product.setQuantity(product.getQuantity() + reservation.getQuantity());
            productRepository.save(product);
            releasedQuantity += reservation.getQuantity();
            log.info("=== Compensation SUCCESS — {} stock restored to {}", reservation.getProductName(), product.getQuantity());
            auditPublisher.publish("STOCK_RESTORED", event.userEmail(), "PRODUCT",
                    String.valueOf(product.getId()), reservation);
        }

        CompensationLogJpaEntity entry = new CompensationLogJpaEntity();
        entry.setOrderId(event.orderId());
        entry.setProductName(reservations.get(0).getProductName());
        entry.setQuantity(releasedQuantity);
        compensationLogRepository.save(entry);
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}
