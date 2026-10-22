package com.daniellaera.paymentservice.adapter.in.web;

import com.daniellaera.paymentservice.TestcontainersConfiguration;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the exact error bodies of the REST API. Only HTTP is used, so the same test passes against the code
 * before the hexagonal refactor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentErrorResponsesITTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownTransaction_returns404Body() throws Exception {
        mockMvc.perform(get("/transactions/987654321"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("{\"status\":404,\"message\":\"Transaction not found with id: 987654321\"}"));
    }

    @Test
    void invalidIntentRequest_returnsValidationBody() throws Exception {
        mockMvc.perform(post("/payments/create-intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"currency\":\"usd\",\"productName\":\"MacBook Pro\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"status\":400,\"error\":\"Validation Failed\","
                        + "\"errors\":{\"amount\":\"Amount must be at least 1\"}}"));
    }

    @Test
    void stripeFailure_returnsTheGeneric500Body() throws Exception {
        // Without an API key the Stripe SDK fails before any network call; the key is global, so restore it
        String apiKey = Stripe.apiKey;
        Stripe.apiKey = "";
        try {
            mockMvc.perform(post("/payments/create-intent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":1999,\"currency\":\"usd\",\"productName\":\"MacBook Pro\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string("{\"status\":500,\"message\":\"Internal server error\"}"));
        } finally {
            Stripe.apiKey = apiKey;
        }
    }
}
