package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.config.StripeConfig;
import com.daniellaera.paymentservice.dto.PaymentIntentRequest;
import com.daniellaera.paymentservice.dto.PaymentIntentResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentService {

    private final StripeConfig stripeConfig;

    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(request.amount())
                    .setCurrency(request.currency())
                    .setDescription("Purchase: " + request.productName())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("=== Stripe: created PaymentIntent {} for product '{}'", intent.getId(), request.productName());

            return new PaymentIntentResponse(
                    intent.getClientSecret(),
                    intent.getId(),
                    intent.getStatus(),
                    stripeConfig.getPublishableKey()
            );
        } catch (StripeException e) {
            log.error("=== Stripe: failed to create PaymentIntent: {}", e.getMessage());
            throw new RuntimeException("Stripe payment failed: " + e.getMessage());
        }
    }

    public boolean confirmPayment(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            log.info("=== Stripe: PaymentIntent {} status: {}", paymentIntentId, intent.getStatus());
            return "succeeded".equals(intent.getStatus());
        } catch (StripeException e) {
            log.error("=== Stripe: failed to confirm payment: {}", e.getMessage());
            return false;
        }
    }
}
