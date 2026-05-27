package com.daniellaera.gatewayservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {com.daniellaera.gatewayservice.GatewayServiceApplication.class, FallbackControllerTest.TestConfig.class})
@Import({com.daniellaera.gatewayservice.utils.JwtUtil.class, com.daniellaera.gatewayservice.TestcontainersConfiguration.class})
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.cloud.config.enabled=false"
})
public class FallbackControllerTest {

    @Configuration
    static class TestConfig {
        @Bean
        public WebTestClient webTestClient(org.springframework.context.ApplicationContext context) {
            return WebTestClient.bindToApplicationContext(context)
                    .apply(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
                    .configureClient()
                    .build();
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @WithMockUser
    void ordersFallback_shouldReturnServiceUnavailable() {
        webTestClient.mutateWith(csrf())
                .get().uri("/fallback/orders")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Order service is currently unavailable. Please try again later.");
    }

    @Test
    @WithMockUser
    void productsFallback_shouldReturnServiceUnavailable() {
        webTestClient.mutateWith(csrf())
                .get().uri("/fallback/products")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Inventory service is currently unavailable. Please try again later.");
    }

    @Test
    @WithMockUser
    void transactionsFallback_shouldReturnServiceUnavailable() {
        webTestClient.mutateWith(csrf())
                .get().uri("/fallback/transactions")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Payment service is currently unavailable. Please try again later.");
    }
}
