package com.daniellaera.gatewayservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(FallbackController.class)
@Import(com.daniellaera.gatewayservice.utils.JwtUtil.class)
@TestPropertySource(properties = "spring.cloud.config.enabled=false")
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @ParameterizedTest
    @CsvSource({
            "/fallback/orders, Order service is currently unavailable. Please try again later.",
            "/fallback/products, Inventory service is currently unavailable. Please try again later.",
            "/fallback/transactions, Payment service is currently unavailable. Please try again later.",
            "/fallback/payments, Payment service is currently unavailable. Please try again later."
    })
    void fallback_shouldReturnServiceUnavailable(String uri, String expectedMessage) {
        webTestClient
                .get().uri(uri)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo(expectedMessage);
    }

    @Test
    void paymentsFallback_answersThePostThatTimedOut() {
        // the circuit breaker forwards the original request, e.g. POST /payments/create-intent
        webTestClient
                .post().uri("/fallback/payments")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Payment service is currently unavailable. Please try again later.");
    }

    @Test
    void orderStreamFallback_answersTheSseRequestWithJson() {
        // the forwarded request still asks for text/event-stream
        webTestClient
                .get().uri("/fallback/order-stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Order updates are currently unavailable. Reconnecting shortly.");
    }
}
