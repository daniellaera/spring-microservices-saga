package com.daniellaera.sagae2e.steps;

import com.daniellaera.sagae2e.support.SagaEnvironment;
import com.daniellaera.sagae2e.support.WireMockStubs;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.cucumber.java.en.Then;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** What payment-service asked Stripe to refund, read from WireMock's request journal. */
public class RefundSteps {

    // How long "no further refund" must hold
    private static final Duration STEADY = Duration.ofSeconds(3);

    private final ScenarioContext context;
    private final WireMockStubs stripe = SagaEnvironment.get().stubs();

    public RefundSteps(ScenarioContext context) {
        this.context = context;
    }

    @Then("the payment of the order is refunded once")
    public void thePaymentIsRefundedOnce() {
        String intentId = context.lastIntentId();
        await().untilAsserted(() -> assertThat(stripe.refundRequestsFor(intentId)).isNotEmpty());
        await().during(STEADY).atMost(STEADY.plusSeconds(10))
                .untilAsserted(() -> assertThat(stripe.refundRequestsFor(intentId)).hasSize(1));
        LoggedRequest refund = stripe.refundRequestsFor(intentId).get(0);
        assertThat(refund.getHeader("Idempotency-Key")).isEqualTo("refund-order-" + context.orderId());
    }

    @Then("the payment intent is never refunded")
    public void thePaymentIntentIsNeverRefunded() {
        String intentId = context.lastIntentId();
        await().during(STEADY).atMost(STEADY.plusSeconds(10))
                .untilAsserted(() -> assertThat(stripe.refundRequestsFor(intentId)).isEmpty());
    }
}
