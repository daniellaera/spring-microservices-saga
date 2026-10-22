package com.daniellaera.paymentservice.domain.port.in;

public interface VerifyPaymentQuery {
    /** False when the intent has not succeeded or the provider cannot be asked. */
    boolean isSucceeded(String paymentIntentId);
}
