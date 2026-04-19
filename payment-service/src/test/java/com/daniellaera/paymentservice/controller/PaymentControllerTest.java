package com.daniellaera.paymentservice.controller;

import com.daniellaera.paymentservice.dto.TransactionDTO;
import com.daniellaera.paymentservice.enums.PaymentStatus;
import com.daniellaera.paymentservice.exception.GlobalExceptionHandler;
import com.daniellaera.paymentservice.exception.ResourceNotFoundException;
import com.daniellaera.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    private PaymentService paymentService;

    private MockMvc mockMvc;

    private final TransactionDTO dto = new TransactionDTO(1L, 1L, PaymentStatus.SUCCESS);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllTransactions_shouldReturn200() throws Exception {
        when(paymentService.getAllTransactions()).thenReturn(List.of(dto));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void getTransactionById_shouldReturn200() throws Exception {
        when(paymentService.getTransactionById(1L)).thenReturn(dto);

        mockMvc.perform(get("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void getTransactionById_shouldReturn404_whenNotFound() throws Exception {
        when(paymentService.getTransactionById(999L))
                .thenThrow(new ResourceNotFoundException("Transaction not found with id: 999"));

        mockMvc.perform(get("/transactions/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found with id: 999"));
    }
}