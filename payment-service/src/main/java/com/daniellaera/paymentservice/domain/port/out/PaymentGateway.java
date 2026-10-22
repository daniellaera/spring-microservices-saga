package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;

public interface PaymentGateway {
    /** Throws PaymentGatewayException when the provider fails. */
    CreatedPaymentIntent createIntent(long amount, String currency, String description);

    /** True only when the provider reports the intent as succeeded; false on any provider error. */
    boolean isSucceeded(String intentId);
}
