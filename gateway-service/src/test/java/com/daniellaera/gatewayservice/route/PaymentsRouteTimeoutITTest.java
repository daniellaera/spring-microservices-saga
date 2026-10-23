package com.daniellaera.gatewayservice.route;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** payment-cb as configured for production: 10 s, not resilience4j's 1 s default. */
class PaymentsRouteTimeoutITTest extends PaymentsRouteITSupport {

    @Test
    void createIntentSlowerThanOneSecond_isAnsweredByPaymentService() {
        // the first create-intent after a payment-service deploy took just over 1 s and became a 404
        paymentServiceAnswersCreateIntentAfter(1500);

        long start = System.nanoTime();
        createIntent()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paymentIntentId").isEqualTo("pi_1");

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(1500);
    }
}
