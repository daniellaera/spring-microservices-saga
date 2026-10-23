package com.daniellaera.gatewayservice.route;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Past the payment-cb time limit, shortened here from 10 s to 2 s to keep the test fast. */
@TestPropertySource(properties = "resilience4j.timelimiter.instances.payment-cb.timeout-duration=2s")
class PaymentsRouteFallbackITTest extends PaymentsRouteITSupport {

    @Test
    void createIntentPastTheTimeLimit_isAnswered503ByThePaymentsFallback() {
        paymentServiceAnswersCreateIntentAfter(4000);

        long start = System.nanoTime();
        createIntent()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("503")
                .jsonPath("$.message").isEqualTo("Payment service is currently unavailable. Please try again later.");

        // the configured instance limit applies, not the 1 s default
        assertThat((System.nanoTime() - start) / 1_000_000).isBetween(2000L, 3900L);
    }
}
