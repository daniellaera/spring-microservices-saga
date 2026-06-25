package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.dto.InventoryResultEvent;
import com.daniellaera.paymentservice.dto.TransactionDTO;
import com.daniellaera.paymentservice.enums.PaymentStatus;
import com.daniellaera.paymentservice.exception.ResourceNotFoundException;
import com.daniellaera.paymentservice.model.Transaction;
import com.daniellaera.paymentservice.repository.TransactionRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private StripePaymentService stripePaymentService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new Transaction();
        transaction.setOrderId(1L);
        transaction.setStatus(PaymentStatus.SUCCESS);
        transaction.setTotalAmount(BigDecimal.valueOf(999.99));
    }

    // ─── getAllTransactions / getTransactionById ──────────────────────────────

    @Test
    void getAllTransactions_shouldReturnListOfDTOs() {
        when(transactionRepository.findAll()).thenReturn(List.of(transaction));

        List<TransactionDTO> result = paymentService.getAllTransactions();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().orderId()).isEqualTo(1L);
        assertThat(result.getFirst().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getFirst().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
        verify(transactionRepository, times(1)).findAll();
    }

    @Test
    void getTransactionById_shouldReturnDTO_whenExists() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        TransactionDTO result = paymentService.getTransactionById(1L);

        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
    }

    @Test
    void getTransactionById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getTransactionById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ─── handleInventoryResult ────────────────────────────────────────────────

    @Test
    void handleInventoryResult_approved_stripeSuccess_savesSuccessTransactionAndPublishes() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(1L, "APPROVED", "MacBook Pro", 1,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(1299.99), "user@test.com", "pi_123");
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(stripePaymentService.confirmPayment("pi_123")).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(txCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(txCaptor.getValue().getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1299.99));

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
        verify(stripePaymentService).confirmPayment("pi_123");
    }

    @Test
    void handleInventoryResult_approved_stripeFailure_savesFailedTransactionAndPublishes() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(2L, "APPROVED", "iPhone", 1,
                BigDecimal.valueOf(999.99), BigDecimal.valueOf(999.99), "user@test.com", "pi_456");
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(stripePaymentService.confirmPayment("pi_456")).thenReturn(false);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(txCaptor.getValue().getOrderId()).isEqualTo(2L);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
    }

    @Test
    void handleInventoryResult_approved_noPaymentIntentId_autoApprovesWithoutStripe() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(3L, "APPROVED", "AirPods", 1,
                BigDecimal.valueOf(249.99), BigDecimal.valueOf(249.99), "user@test.com", null);
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        verifyNoInteractions(stripePaymentService);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
    }

    @Test
    void handleInventoryResult_rejected_skipStripeAndSavesFailed() throws Exception {
        InventoryResultEvent event = new InventoryResultEvent(4L, "REJECTED", "MacBook Pro", 0,
                BigDecimal.valueOf(1299.99), BigDecimal.valueOf(1299.99), "user@test.com", "pi_789");
        when(objectMapper.readValue(anyString(), eq(InventoryResultEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        paymentService.handleInventoryResult("{}");

        verifyNoInteractions(stripePaymentService);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(txCaptor.getValue().getOrderId()).isEqualTo(4L);

        verify(kafkaTemplate).send(eq("payment-topic"), anyString());
    }
}
