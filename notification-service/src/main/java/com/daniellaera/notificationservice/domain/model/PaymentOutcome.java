package com.daniellaera.notificationservice.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The payment result of an order, as payment-service announced it: status SUCCESS or FAILED, the customer's email
 * (may be missing) and the order's items. Orders from before multi-item support carry only the legacy single-item
 * fields.
 */
public record PaymentOutcome(
        Long orderId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        String status,
        String userEmail,
        List<Item> items
) {

    public record Item(String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount) {}

    public boolean succeeded() {
        return "SUCCESS".equals(status);
    }

    public boolean failed() {
        return "FAILED".equals(status);
    }

    public boolean hasRecipient() {
        return userEmail != null && !userEmail.isBlank();
    }

    // the items, or one line from the legacy fields when there are none
    public List<Item> lines() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        return List.of(new Item(productName, quantity, price, totalAmount));
    }
}
