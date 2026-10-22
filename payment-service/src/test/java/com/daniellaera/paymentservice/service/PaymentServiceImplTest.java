package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.audit.AuditPublisher;
import com.daniellaera.paymentservice.dto.InventoryResultEvent;
import com.daniellaera.paymentservice.dto.OrderItemEvent;
import com.daniellaera.paymentservice.dto.PaymentEvent;
import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.daniellaera.paymentservice.adapter.out.persistence.TransactionJpaEntity;
import com.daniellaera.paymentservice.adapter.out.persistence.TransactionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private TransactionJpaRepository transactionRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private PaymentGateway paymentGateway;
    @Mock private AuditPublisher auditPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private TransactionJpaEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionJpaEntity();
        transaction.setOrderId(1L);
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setTotalAmount(BigDecimal.valueOf(999.99));
    }

    // ─── handleInventoryResult ────────────────────────────────────────────────

    @Test
    void handleInventoryResult_approved_stripeSuccess_savesSuccessTransactionAndPublishes() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(1L, "APPROVED", "MacBook Pro", 1,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(1299.99), "user@test.com", "pi_123", null);
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(paymentGateway.isSucceeded("pi_123")).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        ArgumentCaptor<TransactionJpaEntity> txCaptor = ArgumentCaptor.forClass(TransactionJpaEntity.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(txCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(txCaptor.getValue().getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1299.99));

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
        verify(paymentGateway).isSucceeded("pi_123");
        verify(auditPublisher).publish(eq("PAYMENT_SUCCEEDED"), any(), any(), any(), any());
    }

    @Test
    void handleInventoryResult_approved_itemsListPassedThroughToPaymentEvent() throws Exception {
        List<OrderItemEvent> items = List.of(
                new OrderItemEvent("MacBook Pro", 1, BigDecimal.valueOf(1299.99), BigDecimal.valueOf(1299.99)));
        InventoryResultEvent event = new InventoryResultEvent(5L, "APPROVED", "MacBook Pro", 1,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(1299.99), "user@test.com", "pi_999", items);
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(paymentGateway.isSucceeded("pi_999")).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());
        assertThat(eventCaptor.getValue().items()).isEqualTo(items);
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(5L);
    }

    @Test
    void handleInventoryResult_approved_stripeFailure_savesFailedTransactionAndPublishes() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(2L, "APPROVED", "iPhone", 1,
                BigDecimal.valueOf(999.99), BigDecimal.valueOf(999.99), "user@test.com", "pi_456", null);
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(paymentGateway.isSucceeded("pi_456")).thenReturn(false);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        ArgumentCaptor<TransactionJpaEntity> txCaptor = ArgumentCaptor.forClass(TransactionJpaEntity.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(txCaptor.getValue().getOrderId()).isEqualTo(2L);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
        verify(auditPublisher).publish(eq("PAYMENT_FAILED"), any(), any(), any(), any());
    }

    @Test
    void handleInventoryResult_approved_noPaymentIntentId_autoApprovesWithoutStripe() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(3L, "APPROVED", "AirPods", 1,
                BigDecimal.valueOf(249.99), BigDecimal.valueOf(249.99), "user@test.com", null, null);
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        verifyNoInteractions(paymentGateway);

        ArgumentCaptor<TransactionJpaEntity> txCaptor = ArgumentCaptor.forClass(TransactionJpaEntity.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
    }

    @Test
    void handleInventoryResult_rejected_skipStripeAndSavesFailed() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(4L, "REJECTED", "MacBook Pro", 0,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(1299.99), "user@test.com", "pi_789", null);
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        verifyNoInteractions(paymentGateway);

        ArgumentCaptor<TransactionJpaEntity> txCaptor = ArgumentCaptor.forClass(TransactionJpaEntity.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(txCaptor.getValue().getOrderId()).isEqualTo(4L);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
    }
}
