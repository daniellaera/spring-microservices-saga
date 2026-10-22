package com.daniellaera.paymentservice.domain.port.in;

public interface CreatePaymentIntentUseCase {
    /** Throws PaymentGatewayException when the provider fails. */
    CreatedPaymentIntent createIntent(CreatePaymentIntentCommand command);
}
