package com.daniellaera.paymentservice.controller;

import com.daniellaera.paymentservice.TestcontainersConfiguration;
import com.daniellaera.paymentservice.enums.PaymentStatus;
import com.daniellaera.paymentservice.model.Transaction;
import com.daniellaera.paymentservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PaymentControllerITTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();

        Transaction transaction = new Transaction();
        transaction.setOrderId(1L);
        transaction.setStatus(PaymentStatus.SUCCESS);
        transactionRepository.save(transaction);
    }

    @Test
    void getAllTransactions_shouldReturn200AndList() throws Exception {
        mockMvc.perform(get("/transactions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void getTransactionById_shouldReturn200() throws Exception {
        Transaction saved = transactionRepository.findAll().get(0);

        mockMvc.perform(get("/transactions/" + saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void getTransactionById_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get("/transactions/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}