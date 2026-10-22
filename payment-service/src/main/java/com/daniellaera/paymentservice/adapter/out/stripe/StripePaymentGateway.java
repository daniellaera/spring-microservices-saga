package com.daniellaera.paymentservice.adapter.out.stripe;

import com.daniellaera.paymentservice.domain.model.PaymentGatewayException;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stripe through its static SDK API (the API key is global, see StripeApiKeyInitializer). The only place, with
 * that initializer, allowed to use com.stripe.
 */
@Component
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    @Override
    public CreatedPaymentIntent createIntent(long amount, String currency, String description) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amount)
                    .setCurrency(currency)
                    .setDescription(description)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            return new CreatedPaymentIntent(intent.getClientSecret(), intent.getId(), intent.getStatus());
        } catch (StripeException e) {
            log.error("=== Stripe: failed to create PaymentIntent: {}", e.getMessage());
            throw new PaymentGatewayException("Stripe payment failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isSucceeded(String intentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(intentId);
            log.info("=== Stripe: PaymentIntent {} status: {}", intentId, intent.getStatus());
            return "succeeded".equals(intent.getStatus());
        } catch (StripeException e) {
            log.error("=== Stripe: failed to confirm payment: {}", e.getMessage());
            return false;
        }
    }
}
