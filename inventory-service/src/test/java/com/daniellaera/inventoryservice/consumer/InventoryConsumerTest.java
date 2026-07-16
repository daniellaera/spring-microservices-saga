package com.daniellaera.inventoryservice.consumer;

import com.daniellaera.inventoryservice.audit.AuditPublisher;
import com.daniellaera.inventoryservice.dto.InventoryResultEvent;
import com.daniellaera.inventoryservice.dto.OrderEvent;
import com.daniellaera.inventoryservice.dto.PaymentEvent;
import com.daniellaera.inventoryservice.model.CompensationLog;
import com.daniellaera.inventoryservice.model.Product;
import com.daniellaera.inventoryservice.repository.CompensationLogRepository;
import com.daniellaera.inventoryservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
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

    @Mock private ProductRepository productRepository;
    @Mock private CompensationLogRepository compensationLogRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private AuditPublisher auditPublisher;

    @InjectMocks
    private InventoryConsumer inventoryConsumer;

    private Product macbook;
    private Product airpods;

    @BeforeEach
    void setUp() {
        macbook = new Product();
        macbook.setName("MacBook Pro");
        macbook.setQuantity(10);
        macbook.setPrice(BigDecimal.valueOf(1299.99));

        airpods = new Product();
        airpods.setName("AirPods");
        airpods.setQuantity(5);
        airpods.setPrice(BigDecimal.valueOf(249.99));
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

        ArgumentCaptor<InventoryResultEvent> captor = ArgumentCaptor.forClass(InventoryResultEvent.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("REJECTED");
        assertThat(captor.getValue().quantity()).isEqualTo(0);
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
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));

        inventoryConsumer.handlePaymentResult("{}");

        assertThat(macbook.getQuantity()).isEqualTo(12); // 10 + 2 restored
        verify(productRepository).save(macbook);

        ArgumentCaptor<CompensationLog> logCaptor = ArgumentCaptor.forClass(CompensationLog.class);
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
        when(productRepository.findByName("MacBook Pro")).thenReturn(Optional.of(macbook));
        when(productRepository.findByName("AirPods")).thenReturn(Optional.of(airpods));

        inventoryConsumer.handlePaymentResult("{}");

        assertThat(macbook.getQuantity()).isEqualTo(12);
        assertThat(airpods.getQuantity()).isEqualTo(8);
        verify(productRepository).save(macbook);
        verify(productRepository).save(airpods);
        verify(compensationLogRepository).save(any(CompensationLog.class));
    }

    @Test
    void handlePaymentResult_failed_alreadyCompensated_skipsIdempotently() throws Exception {
        PaymentEvent event = new PaymentEvent(1L, "MacBook Pro", 2, "FAILED",
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(2599.98), "user@test.com", null);
        when(objectMapper.readValue(anyString(), eq(PaymentEvent.class))).thenReturn(event);
        when(compensationLogRepository.existsByOrderId(1L)).thenReturn(true);

        inventoryConsumer.handlePaymentResult("{}");

        verify(productRepository, never()).findByName(any());
        verify(compensationLogRepository, never()).save(any());
    }
}
