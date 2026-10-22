package com.daniellaera.paymentservice.adapter.in.web;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionNotFoundException;
import com.daniellaera.paymentservice.domain.port.in.TransactionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private TransactionQuery transactionQuery;

    private MockMvc mockMvc;

    private final PaymentTransaction transaction =
            new PaymentTransaction(1L, 1L, BigDecimal.valueOf(999.99), PaymentStatus.SUCCESS, "pi_1", null);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllTransactions_shouldReturn200() throws Exception {
        when(transactionQuery.getAllTransactions()).thenReturn(List.of(transaction));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].totalAmount").value(999.99));
    }

    @Test
    void getTransactionById_shouldReturn200() throws Exception {
        when(transactionQuery.getTransactionById(1L)).thenReturn(transaction);

        mockMvc.perform(get("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void getTransactionById_shouldReturn404_whenNotFound() throws Exception {
        when(transactionQuery.getTransactionById(999L))
                .thenThrow(new TransactionNotFoundException(999L));

        mockMvc.perform(get("/transactions/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found with id: 999"));
    }

    @Test
    void getAllTransactions_shouldReturn500_onUnexpectedException() throws Exception {
        when(transactionQuery.getAllTransactions()).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/transactions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
