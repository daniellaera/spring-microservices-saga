package com.daniellaera.paymentservice.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The outcome of one payment attempt for an order. Nothing prevents several transactions for the same order:
 * a redelivered inventory result records another one (current behaviour).
 */
public record PaymentTransaction(Long id, Long orderId, BigDecimal totalAmount, PaymentStatus status,
                                 LocalDateTime createdAt) {

    /** A transaction that has never been persisted: persistence assigns the id and createdAt. */
    public static PaymentTransaction record(Long orderId, BigDecimal totalAmount, boolean succeeded) {
        return new PaymentTransaction(null, orderId, totalAmount,
                succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, null);
    }

    public boolean succeeded() {
        return status == PaymentStatus.SUCCESS;
    }
}
