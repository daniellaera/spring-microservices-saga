package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.adapter.out.stripe.StripePaymentGateway;
import com.daniellaera.paymentservice.audit.AuditPublisher;
import com.daniellaera.paymentservice.config.StripeConfig;
import com.daniellaera.paymentservice.dto.PaymentIntentRequest;
import com.daniellaera.paymentservice.dto.PaymentIntentResponse;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentService {

    private final StripeConfig stripeConfig;
    private final AuditPublisher auditPublisher;
    private final StripePaymentGateway paymentGateway;

    public PaymentIntentResponse createPaymentIntent(PaymentIntentRequest request, String userEmail) {
        CreatedPaymentIntent intent = paymentGateway.createIntent(request.amount(), request.currency(),
                "Purchase: " + request.productName());
        log.info("=== Stripe: created PaymentIntent {} for product '{}'", intent.id(), request.productName());

        auditPublisher.publish("PAYMENT_INITIATED", userEmail, "PAYMENT", intent.id(), request);

        return new PaymentIntentResponse(
                intent.clientSecret(),
                intent.id(),
                intent.status(),
                stripeConfig.getPublishableKey()
        );
    }

    public boolean confirmPayment(String paymentIntentId) {
        return paymentGateway.isSucceeded(paymentIntentId);
    }
}
