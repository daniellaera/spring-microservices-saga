package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayUnavailableException;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;

import java.util.Optional;

public interface PaymentGateway {
    /** Throws PaymentGatewayException when the provider fails. */
    CreatedPaymentIntent createIntent(long amount, String currency, String description);

    /**
     * The provider's view of the intent; empty when the provider refuses the request (e.g. an unknown intent).
     *
     * @throws PaymentGatewayUnavailableException when the provider cannot answer for now
     */
    Optional<IntentVerification> verifyIntent(String intentId);
}
