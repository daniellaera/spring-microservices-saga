package com.daniellaera.paymentservice.controller;

import com.daniellaera.paymentservice.dto.PaymentIntentResponse;
import com.daniellaera.paymentservice.service.StripePaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StripePaymentControllerTest {

    @InjectMocks
    private StripePaymentController stripePaymentController;

    @Mock
    private StripePaymentService stripePaymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(stripePaymentController).build();
    }

    // ─── POST /payments/create-intent ──────────────────────────────────────

    @Test
    void createIntent_validBody_returns200WithClientSecret() throws Exception {
        when(stripePaymentService.createPaymentIntent(any(), any()))
                .thenReturn(new PaymentIntentResponse("secret_123", "pi_123", "requires_payment_method", "pk_test_123"));

        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1999, "currency": "usd", "productName": "MacBook Pro"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("secret_123"))
                .andExpect(jsonPath("$.paymentIntentId").value("pi_123"));
    }

    @Test
    void createIntent_missingAmount_returns400() throws Exception {
        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency": "usd", "productName": "MacBook Pro"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIntent_missingCurrency_returns400() throws Exception {
        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1999, "productName": "MacBook Pro"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIntent_missingProductName_returns400() throws Exception {
        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1999, "currency": "usd"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /payments/confirm/{id} ────────────────────────────────────────

    @Test
    void confirmPayment_succeeded_returnsSuccessTrue() throws Exception {
        when(stripePaymentService.confirmPayment("pi_123")).thenReturn(true);

        mockMvc.perform(get("/payments/confirm/pi_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void confirmPayment_notSucceeded_returnsSuccessFalse() throws Exception {
        when(stripePaymentService.confirmPayment("pi_456")).thenReturn(false);

        mockMvc.perform(get("/payments/confirm/pi_456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
