package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.model.IntentVerification;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayUnavailableException;
import com.daniellaera.paymentservice.domain.port.in.VerifyPaymentQuery;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;

public class VerifyPaymentService implements VerifyPaymentQuery {

    private final PaymentGateway paymentGateway;

    public VerifyPaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Override
    public boolean isSucceeded(String paymentIntentId) {
        try {
            return paymentGateway.verifyIntent(paymentIntentId).map(IntentVerification::succeeded).orElse(false);
        } catch (PaymentGatewayUnavailableException e) {
            // the shop's confirmation check answers as it always did when Stripe fails: not succeeded
            return false;
        }
    }
}
