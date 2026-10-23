package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.application.fakes.FakePaymentGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ports the confirmPayment scenarios of the former StripePaymentServiceTest. Which provider statuses count as
 * success, and the provider-error case, are pinned at the HTTP level by StripeGatewayITTest.
 */
class VerifyPaymentServiceTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final VerifyPaymentService service = new VerifyPaymentService(gateway);

    @Test
    void succeededIntent_isASuccess() {
        gateway.givenIntent("pi_123", "succeeded");

        assertThat(service.isSucceeded("pi_123")).isTrue();
    }

    @Test
    void requiresPaymentMethodOrCanceled_isNotASuccess() {
        gateway.givenIntent("pi_1", "requires_payment_method");
        gateway.givenIntent("pi_2", "canceled");

        assertThat(service.isSucceeded("pi_1")).isFalse();
        assertThat(service.isSucceeded("pi_2")).isFalse();
    }

    @Test
    void providerError_isNotASuccess() {
        assertThat(service.isSucceeded("pi_missing")).isFalse();
    }

    @Test
    void providerUnavailable_isNotASuccessEither() {
        gateway.givenIntent("pi_123", "succeeded");
        gateway.unavailableFor(1);

        assertThat(service.isSucceeded("pi_123")).isFalse();
    }
}
