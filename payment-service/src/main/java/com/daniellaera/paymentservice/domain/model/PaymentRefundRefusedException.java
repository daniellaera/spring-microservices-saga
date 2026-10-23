package com.daniellaera.paymentservice.domain.model;

/** The provider refused to refund the intent for good (a 4xx other than "already refunded"): it needs a human. */
public class PaymentRefundRefusedException extends RuntimeException {

    private final String errorCode;

    public PaymentRefundRefusedException(String paymentIntentId, String errorCode, String message) {
        super("Refund of " + paymentIntentId + " refused: " + message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
