package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.port.in.VerifyPaymentQuery;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;

public class VerifyPaymentService implements VerifyPaymentQuery {

    private final PaymentGateway paymentGateway;

    public VerifyPaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Override
    public boolean isSucceeded(String paymentIntentId) {
        return paymentGateway.isSucceeded(paymentIntentId);
    }
}
