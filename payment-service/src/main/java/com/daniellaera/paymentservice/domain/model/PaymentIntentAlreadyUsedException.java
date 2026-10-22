package com.daniellaera.paymentservice.domain.model;

/** The payment intent already paid for another order: an intent pays for one order at most. */
public class PaymentIntentAlreadyUsedException extends RuntimeException {

    public PaymentIntentAlreadyUsedException(String paymentIntentId, Throwable cause) {
        super("Payment intent " + paymentIntentId + " already paid for another order", cause);
    }
}
