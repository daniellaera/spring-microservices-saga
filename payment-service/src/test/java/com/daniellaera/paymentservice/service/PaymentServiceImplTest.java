package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.dto.TransactionDTO;
import com.daniellaera.paymentservice.enums.PaymentStatus;
import com.daniellaera.paymentservice.exception.ResourceNotFoundException;
import com.daniellaera.paymentservice.model.Transaction;
import com.daniellaera.paymentservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new Transaction();
        transaction.setOrderId(1L);
        transaction.setStatus(PaymentStatus.SUCCESS);
    }

    @Test
    void getAllTransactions_shouldReturnListOfDTOs() {
        when(transactionRepository.findAll()).thenReturn(List.of(transaction));

        List<TransactionDTO> result = paymentService.getAllTransactions();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().orderId()).isEqualTo(1L);
        assertThat(result.getFirst().status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(transactionRepository, times(1)).findAll();
    }

    @Test
    void getTransactionById_shouldReturnDTO_whenExists() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        TransactionDTO result = paymentService.getTransactionById(1L);

        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void getTransactionById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getTransactionById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}