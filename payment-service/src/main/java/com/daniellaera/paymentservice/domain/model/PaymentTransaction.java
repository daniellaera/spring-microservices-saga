package com.daniellaera.paymentservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The outcome of the payment of an order. There is at most one per order (unique constraint). paymentIntentId is
 * the intent that paid for it: set on a success only, and an intent pays for one order at most. refundId is the
 * refund that gave a captured payment back.
 */
public record PaymentTransaction(Long id, Long orderId, BigDecimal totalAmount, PaymentStatus status,
                                 String paymentIntentId, String refundId, LocalDateTime createdAt) {

    /** A transaction that has never been persisted, without a paying intent: persistence assigns id and createdAt. */
    public static PaymentTransaction record(Long orderId, BigDecimal totalAmount, boolean succeeded) {
        return new PaymentTransaction(null, orderId, totalAmount,
                succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, null, null, null);
    }

    /** A successful payment by this intent that has never been persisted. */
    public static PaymentTransaction paid(Long orderId, BigDecimal totalAmount, String paymentIntentId) {
        return new PaymentTransaction(null, orderId, totalAmount, PaymentStatus.SUCCESS, paymentIntentId, null, null);
    }

    public boolean succeeded() {
        return status == PaymentStatus.SUCCESS;
    }
}
