package com.daniellaera.orderservice.domain.port.in;

import java.math.BigDecimal;

/**
 * Outcome of a payment as received on payment-topic. Components mirror the inbound PaymentEvent exactly,
 * because this record is also the ORDER_CONFIRMED / ORDER_CANCELLED audit payload.
 */
public record PaymentResult(Long orderId, String productName, Integer quantity, String status,
                            BigDecimal price, BigDecimal totalAmount, String userEmail) {

    public boolean succeeded() {
        return "SUCCESS".equals(status);
    }
}
