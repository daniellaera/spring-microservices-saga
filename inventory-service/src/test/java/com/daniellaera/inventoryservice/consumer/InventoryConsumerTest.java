package com.daniellaera.inventoryservice.consumer;

import com.daniellaera.inventoryservice.audit.AuditPublisher;
import com.daniellaera.inventoryservice.dto.InventoryResultEvent;
import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.dto.PaymentEvent;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaEntity;
import com.daniellaera.inventoryservice.adapter.out.persistence.CompensationLogJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.ProductJpaRepository;
import com.daniellaera.inventoryservice.adapter.out.persistence.StockReservationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import com.daniellaera.inventoryservice.dto.OrderItemEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryConsumerTest {

    @Mock private ProductJpaRepository productRepository;
    @Mock private CompensationLogJpaRepository compensationLogRepository;
    @Mock private StockReservationJpaRepository stockReservationRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private AuditPublisher auditPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private InventoryConsumer inventoryConsumer;

    private ProductJpaEntity macbook;
    private ProductJpaEntity airpods;

    @BeforeEach
    void setUp() {
        macbook = new ProductJpaEntity();
        macbook.setName("MacBook Pro");
        macbook.setQuantity(10);
        macbook.setPrice(BigDecimal.valueOf(1299.99));

        airpods = new ProductJpaEntity();
        airpods.setName("AirPods");
        airpods.setQuantity(5);
        airpods.setPrice(BigDecimal.valueOf(249.99));

        // run the transactional callback inline with synchronization active: afterCommit hooks fire only
        // when the callback returns normally; an exception propagates and the hooks are dropped (rollback)
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            TransactionSynchronizationManager.initSynchronization();
            try {
                Object result = callback.doInTransaction(new SimpleTransactionStatus());
                TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
                return result;
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        });
    }

    private static StockReservationJpaEntity reservation(Long orderId, String productName, int quantity) {
        StockReservationJpaEntity r = new StockReservationJpaEntity();
        r.setOrderId(orderId);
        r.setProductName(productName);
        r.setQuantity(quantity);
        return r;
    }

    // ─── consumeOrder ────────────────────────────────────────────────────────

    @Test
    void consumeOrder_sufficientStock_deductsStockAndPublishesApprovedWithPaymentIntentId() throws Exception {
        OrderEvent event = new OrderEvent(1L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", "pi_123", null);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryConsumer.consumeOrder("{}");

        assertThat(macbook.getQuantity()).isEqualTo(8); // 10 - 2
        verify(productRepository).save(macbook);

        ArgumentCaptor<StockReservationJpaEntity> reservationCaptor = ArgumentCaptor.forClass(StockReservationJpaEntity.class);
        verify(stockReservationRepository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(reservationCaptor.getValue().getProductName()).isEqualTo("MacBook Pro");
        assertThat(reservationCaptor.getValue().getQuantity()).isEqualTo(2);
        verify(auditPublisher).publish(eq("STOCK_DEDUCTED"), eq("user@test.com"), eq("PRODUCT"), any(), any());

        ArgumentCaptor<InventoryResultEvent> captor = ArgumentCaptor.forClass(InventoryResultEvent.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("APPROVED");
        assertThat(captor.getValue().quantity()).isEqualTo(2);
        assertThat(captor.getValue().paymentIntentId()).isEqualTo("pi_123");
        assertThat(captor.getValue().orderId()).isEqualTo(1L);

        verify(kafkaTemplate).send(eq("inventory-topic"), anyString());
    }

    @Test
    void consumeOrder_insufficientStock_publishesRejectedWithoutDeductingStock() throws Exception {
        OrderEvent event = new OrderEvent(2L, "MacBook Pro", 20,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(25999.80), "user@test.com", null, null);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryConsumer.consumeOrder("{}");

        assertThat(macbook.getQuantity()).isEqualTo(10); // unchanged
        verify(productRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());

        ArgumentCaptor<InventoryResultEvent> captor = ArgumentCaptor.forClass(InventoryResultEvent.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REJECTED");
        assertThat(captor.getValue().quantity()).isEqualTo(0);

        verify(kafkaTemplate).send(eq("inventory-topic"), anyString());
    }

    @Test
    void consumeOrder_productNotFound_logsErrorAndPublishesNothing() throws Exception {
        OrderEvent event = new OrderEvent(3L, "Unknown Product", 1,
                BigDecimal.valueOf(100.00), BigDecimal.valueOf(100.00), "user@test.com", null, null);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("Unknown Product")).thenReturn(Optional.empty());

        inventoryConsumer.consumeOrder("{}");

        verify(kafkaTemplate, never()).send(anyString(), anyString());
        verify(productRepository, never()).save(any());
    }

    @Test
    void consumeOrder_multiItemAllInStock_deductsAllAndPublishesApproved() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 2, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98)),
                new OrderItemEvent("AirPods", 3, BigDecimal.valueOf(249.99), BigDecimal.valueOf(749.97))
        );
        OrderEvent event = new OrderEvent(4L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(3349.95), "user@test.com", "pi_multi", items);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(productRepository.findByName("AirPods")).thenReturn(Optional.of(airpods));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryConsumer.consumeOrder("{}");

        assertThat(macbook.getQuantity()).isEqualTo(8);
        assertThat(airpods.getQuantity()).isEqualTo(2);
        verify(productRepository).save(macbook);
        verify(productRepository).save(airpods);
        verify(stockReservationRepository, times(2)).save(any(StockReservationJpaEntity.class));

        ArgumentCaptor<InventoryResultEvent> captor = ArgumentCaptor.forClass(InventoryResultEvent.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("APPROVED");
        assertThat(captor.getValue().items()).hasSize(2);
    }

    @Test
    void consumeOrder_multiItemOneOutOfStock_rejectsWithoutDeductingAnyItem() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 2, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98)),
                new OrderItemEvent("AirPods", 99, BigDecimal.valueOf(249.99), BigDecimal.valueOf(24749.01))
        );
        OrderEvent event = new OrderEvent(5L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(27348.99), "user@test.com", null, items);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(productRepository.findByName("AirPods")).thenReturn(Optional.of(airpods));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryConsumer.consumeOrder("{}");

        assertThat(macbook.getQuantity()).isEqualTo(10);
        assertThat(airpods.getQuantity()).isEqualTo(5);
        verify(productRepository, never()).save(any());
        verify(stockReservationRepository, never()).save(any());

        ArgumentCaptor<InventoryResultEvent> captor = ArgumentCaptor.forClass(InventoryResultEvent.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REJECTED");
        assertThat(captor.getValue().quantity()).isEqualTo(0);
    }

    @Test
    void consumeOrder_redelivery_deductsStockOnceAndPublishesOnce() throws Exception {
        OrderEvent event = new OrderEvent(1L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", "pi_123", null);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        // first delivery finds no reservation, the redelivery finds the one written by the first
        when(stockReservationRepository.existsByOrderId(1L)).thenReturn(false, true);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryConsumer.consumeOrder("{}");
        inventoryConsumer.consumeOrder("{}");

        assertThat(macbook.getQuantity()).isEqualTo(8); // deducted once, not twice
        verify(transactionTemplate, times(1)).execute(any());
        verify(stockReservationRepository, times(1)).save(any(StockReservationJpaEntity.class));
        verify(productRepository, times(1)).save(macbook);
        verify(kafkaTemplate, times(1)).send(eq("inventory-topic"), anyString());
    }

    @Test
    void consumeOrder_reservationInsertFails_leavesStockUnchangedAndPublishesNothing() throws Exception {
        OrderEvent event = new OrderEvent(1L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", "pi_123", null);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        // e.g. the unique constraint on (order_id, product_name) firing on a concurrent duplicate
        when(stockReservationRepository.save(any(StockReservationJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate reservation"));

        inventoryConsumer.consumeOrder("{}");

        assertThat(macbook.getQuantity()).isEqualTo(10);
        verify(productRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString());
        verify(auditPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void consumeOrder_failureAfterFirstDeduction_rollsBackWithoutEmittingAudit() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 2, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98)),
                new OrderItemEvent("AirPods", 3, BigDecimal.valueOf(249.99), BigDecimal.valueOf(749.97))
        );
        OrderEvent event = new OrderEvent(9L, "MacBook Pro", 2,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(3349.95), "user@test.com", null, items);
        when(objectMapper.readValue(anyString(), eq(OrderEvent.class))).thenReturn(event);
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(productRepository.findByName("AirPods")).thenReturn(Optional.of(airpods));
        // MacBook reservation + deduction go through, the AirPods reservation insert fails
        when(stockReservationRepository.save(any(StockReservationJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new DataIntegrityViolationException("simulated insert failure"));

        inventoryConsumer.consumeOrder("{}");

        verify(productRepository).save(macbook); // deducted inside the transaction, rolled back by the DB
        verify(auditPublisher, never()).publish(any(), any(), any(), any(), any());
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    // ─── handlePaymentResult (compensation) ──────────────────────────────────

    @Test
    void handlePaymentResult_success_skipsCompensation() throws Exception {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2, "SUCCESS",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", null);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);

        inventoryConsumer.handlePaymentResult("{}");

        verify(productRepository, never()).findByName(any());
        verify(compensationLogRepository, never()).save(any());
    }

    @Test
    void handlePaymentResult_failed_restoresStockAndSavesCompensationLog() throws Exception {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2, "FAILED",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", null);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);
        when(compensationLogRepository.existsByOrderId(1L)).thenReturn(false);
        when(stockReservationRepository.findByOrderId(1L))
                .thenReturn(List.of(reservation(1L, "MacBook Pro", 2)));
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));

        inventoryConsumer.handlePaymentResult("{}");

        assertThat(macbook.getQuantity()).isEqualTo(12); // 10 + 2 restored
        verify(productRepository).save(macbook);

        ArgumentCaptor<CompensationLogJpaEntity> logCaptor = ArgumentCaptor.forClass(CompensationLogJpaEntity.class);
        verify(compensationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(logCaptor.getValue().getProductName()).isEqualTo("MacBook Pro");
        assertThat(logCaptor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void handlePaymentResult_failedMultiItem_restoresStockForAllItems() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 2, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98)),
                new OrderItemEvent("AirPods", 3, BigDecimal.valueOf(249.99), BigDecimal.valueOf(749.97))
        );
        PaymentEvent event = new PaymentEvent(6L, "MacBook Pro", 2, "FAILED",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(3349.95), "user@test.com", items);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);
        when(compensationLogRepository.existsByOrderId(6L)).thenReturn(false);
        when(stockReservationRepository.findByOrderId(6L)).thenReturn(List.of(
                reservation(6L, "MacBook Pro", 2),
                reservation(6L, "AirPods", 3)
        ));
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(productRepository.findByName("AirPods")).thenReturn(Optional.of(airpods));

        inventoryConsumer.handlePaymentResult("{}");

        assertThat(macbook.getQuantity()).isEqualTo(12);
        assertThat(airpods.getQuantity()).isEqualTo(8);
        verify(productRepository).save(macbook);
        verify(productRepository).save(airpods);
        verify(compensationLogRepository).save(any(CompensationLogJpaEntity.class));
    }

    @Test
    void handlePaymentResult_failed_alreadyCompensated_skipsIdempotently() throws Exception {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2, "FAILED",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", null);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);
        when(compensationLogRepository.existsByOrderId(1L)).thenReturn(true);

        inventoryConsumer.handlePaymentResult("{}");

        verify(stockReservationRepository, never()).findByOrderId(any());
        verify(productRepository, never()).findByName(any());
        verify(compensationLogRepository, never()).save(any());
    }

    @Test
    void handlePaymentResult_failedAfterInventoryRejected_releasesNothing() throws Exception {
        // Regression: inventory REJECTED an order for 20 (stock 10), payment then published FAILED with the
        // original items. No reservation was ever made, so compensation must not add 20 to stock.
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 20, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(25999.80))
        );
        PaymentEvent event = new PaymentEvent(7L, "MacBook Pro", 0, "FAILED",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(25999.80), "user@test.com", items);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);
        when(compensationLogRepository.existsByOrderId(7L)).thenReturn(false);
        when(stockReservationRepository.findByOrderId(7L)).thenReturn(List.of());

        inventoryConsumer.handlePaymentResult("{}");

        assertThat(macbook.getQuantity()).isEqualTo(10);
        verify(productRepository, never()).findByName(any());
        verify(productRepository, never()).save(any());
        verify(compensationLogRepository, never()).save(any());
        verify(auditPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void handlePaymentResult_failed_releasesRecordedQuantityNotRequestedQuantity() throws Exception {
        // The payment event carries the requested items; only the recorded reservation decides what is released.
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 20, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(25999.80))
        );
        PaymentEvent event = new PaymentEvent(8L, "MacBook Pro", 20, "FAILED",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(25999.80), "user@test.com", items);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);
        when(compensationLogRepository.existsByOrderId(8L)).thenReturn(false);
        when(stockReservationRepository.findByOrderId(8L))
                .thenReturn(List.of(reservation(8L, "MacBook Pro", 3)));
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));

        inventoryConsumer.handlePaymentResult("{}");

        assertThat(macbook.getQuantity()).isEqualTo(13); // 10 + 3 reserved, not + 20 requested

        ArgumentCaptor<CompensationLogJpaEntity> logCaptor = ArgumentCaptor.forClass(CompensationLogJpaEntity.class);
        verify(compensationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getQuantity()).isEqualTo(3);
    }
}
