package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Inside a transaction, the audit event is published only after the commit, so a rolled-back deduction never
 * emits STOCK_DEDUCTED; outside one it is published right away. The check has to run on the caller's thread,
 * which is why the @Async send lives in KafkaAuditPublisher and not here.
 */
@Component
@RequiredArgsConstructor
public class TransactionAwareKafkaAuditTrail implements AuditTrail {

    private final KafkaAuditPublisher kafkaAuditPublisher;

    @Override
    public void stockDeducted(Product product, OrderLine line, Long orderId, String userEmail) {
        publishAfterCommit("STOCK_DEDUCTED", userEmail, product, orderId, OrderItemEvents.of(line));
    }

    @Override
    public void stockRestored(Product product, StockReservation reservation, String userEmail) {
        publishAfterCommit("STOCK_RESTORED", userEmail, product, reservation.orderId(),
                StockReservationAuditPayload.of(reservation));
    }

    @Override
    public void productCreated(Product product) {
        publishAfterCommit("PRODUCT_CREATED", null, product, null, ProductAuditPayload.of(product));
    }

    @Override
    public void productRestocked(Product product) {
        publishAfterCommit("PRODUCT_RESTOCKED", null, product, null, ProductAuditPayload.of(product));
    }

    // outside any transaction (a @DltHandler): published right away
    @Override
    public void stockReservationDeadLettered(Long orderId, String userEmail, String error) {
        publishDeadLettered("STOCK_RESERVATION_DEAD_LETTERED", orderId, userEmail, error);
    }

    @Override
    public void stockRestoreDeadLettered(Long orderId, String userEmail, String error) {
        publishDeadLettered("STOCK_RESTORE_DEAD_LETTERED", orderId, userEmail, error);
    }

    private void publishDeadLettered(String eventType, Long orderId, String userEmail, String error) {
        kafkaAuditPublisher.publish(eventType, userEmail, "ORDER", orderId != null ? String.valueOf(orderId) : null,
                orderId, new DeadLetterAuditPayload(orderId, error));
    }

    // orderId: the saga's correlation id, null for the product events outside a saga
    private void publishAfterCommit(String eventType, String userEmail, Product product, Long orderId, Object payload) {
        Runnable publish = () -> kafkaAuditPublisher.publish(eventType, userEmail, "PRODUCT",
                String.valueOf(product.id()), orderId, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
