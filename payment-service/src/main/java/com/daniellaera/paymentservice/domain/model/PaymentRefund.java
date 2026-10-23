package com.daniellaera.paymentservice.domain.model;

/** A refund the provider made (or had already made) of an intent's payment. */
public record PaymentRefund(String refundId, String status) {

    /** Stored as the refund id when the payment had already been refunded outside the shop (e.g. by hand). */
    public static final String ALREADY_REFUNDED = "already_refunded";

    public static PaymentRefund alreadyRefunded() {
        return new PaymentRefund(ALREADY_REFUNDED, ALREADY_REFUNDED);
    }
}
