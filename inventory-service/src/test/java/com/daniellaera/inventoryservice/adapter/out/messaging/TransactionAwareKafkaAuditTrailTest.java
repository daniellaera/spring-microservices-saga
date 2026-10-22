package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Runs the trail inside real Spring transactions (driven by a transaction manager with no resource) to prove
 * that an event raised inside a transaction is published after the commit, and never after a rollback.
 */
class TransactionAwareKafkaAuditTrailTest {

    private final KafkaAuditPublisher publisher = mock(KafkaAuditPublisher.class);
    private final TransactionAwareKafkaAuditTrail auditTrail = new TransactionAwareKafkaAuditTrail(publisher);
    private final TransactionTemplate transaction = new TransactionTemplate(new NoResourceTransactionManager());

    private final Product product = Product.restore(11L, "MacBook Pro", 8, new BigDecimal("1299.99"), null);
    private final OrderLine line = new OrderLine("MacBook Pro", 2, new BigDecimal("1299.99"), new BigDecimal("2599.98"));

    @Test
    void insideTransaction_publishesOnlyAfterCommit() {
        transaction.executeWithoutResult(status -> {
            auditTrail.stockDeducted(product, line, "user@test.com");
            verifyNoInteractions(publisher);
        });

        verify(publisher).publish("STOCK_DEDUCTED", "user@test.com", "PRODUCT", "11",
                new OrderItemEvent("MacBook Pro", 2, new BigDecimal("1299.99"), new BigDecimal("2599.98")));
    }

    @Test
    void insideTransaction_rollback_publishesNothing() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            auditTrail.stockDeducted(product, line, "user@test.com");
            throw new IllegalStateException("reservation insert failed");
        })).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void outsideTransaction_publishesImmediately() {
        auditTrail.stockRestored(product, new StockReservation(UUID.randomUUID(), 5L, "MacBook Pro", 2, null),
                "user@test.com");
        auditTrail.productCreated(product);
        auditTrail.productRestocked(product);

        verify(publisher).publish(eq("STOCK_RESTORED"), eq("user@test.com"), eq("PRODUCT"), eq("11"), any());
        verify(publisher).publish(eq("PRODUCT_CREATED"), eq(null), eq("PRODUCT"), eq("11"), any());
        verify(publisher).publish(eq("PRODUCT_RESTOCKED"), eq(null), eq("PRODUCT"), eq("11"), any());
    }

    private static class NoResourceTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
