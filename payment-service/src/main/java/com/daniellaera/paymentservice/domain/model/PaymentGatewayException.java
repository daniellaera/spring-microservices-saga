package com.daniellaera.paymentservice.domain.model;

/** The payment provider refused or failed a request. Not mapped by the REST layer: it ends in the generic 500. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
