package com.daniellaera.paymentservice.domain.model;

/**
 * The payment provider could not answer for now (unreachable, rate limited or a server error). Unlike a refusal,
 * the same request may succeed later, so it is retried instead of recorded as an outcome.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
