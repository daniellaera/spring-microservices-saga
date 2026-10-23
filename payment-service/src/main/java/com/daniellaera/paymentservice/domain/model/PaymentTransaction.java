package com.daniellaera.paymentservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The outcome of the payment of an order. There is at most one per order (unique constraint). paymentIntentId is
 * the intent whose money belongs to this order: the intent that paid for it (SUCCESS), or a captured intent that
 * was refunded, or was to be, because the order failed (FAILED). An intent belongs to one order at most, so a
 * refunded intent can never pay for another order. refundId is the refund that gave the payment back.
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

    /** A failure whose captured payment was refunded. */
    public static PaymentTransaction refunded(Long orderId, BigDecimal totalAmount, String paymentIntentId,
                                              String refundId) {
        return new PaymentTransaction(null, orderId, totalAmount, PaymentStatus.FAILED, paymentIntentId, refundId, null);
    }

    /** A failure whose captured payment the provider refused to refund: its intent stays with this order. */
    public static PaymentTransaction refundRefused(Long orderId, BigDecimal totalAmount, String paymentIntentId) {
        return new PaymentTransaction(null, orderId, totalAmount, PaymentStatus.FAILED, paymentIntentId, null, null);
    }

    /** The same transaction without its intent, for when another order recorded the intent first. */
    public PaymentTransaction withoutIntent() {
        return new PaymentTransaction(id, orderId, totalAmount, status, null, refundId, createdAt);
    }

    public boolean refunded() {
        return refundId != null;
    }

    public boolean succeeded() {
        return status == PaymentStatus.SUCCESS;
    }
}
