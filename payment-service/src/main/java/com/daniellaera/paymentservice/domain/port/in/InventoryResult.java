package com.daniellaera.paymentservice.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inventory's verdict on an order, as received on inventory-topic. Mirrors the event, items included: they are
 * passed through untouched to the payment result.
 */
public record InventoryResult(Long orderId, String status, String productName, Integer quantity, BigDecimal price,
                              BigDecimal totalAmount, String userEmail, String paymentIntentId, List<Item> items) {

    public record Item(String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount) {}

    public boolean approved() {
        return "APPROVED".equals(status);
    }

    public boolean hasPaymentIntent() {
        return paymentIntentId != null && !paymentIntentId.isBlank();
    }
}
