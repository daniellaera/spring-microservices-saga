package com.daniellaera.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
    // Legacy single-item fields — kept for backward compat when items is absent.
    String productName,

    @Min(value = 1, message = "Quantity must be at least 1")
    Integer quantity,

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    BigDecimal price,

    String paymentIntentId,

    List<@Valid OrderItemRequest> items
) {
    public List<OrderItemRequest> resolvedItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productName != null && quantity != null) {
            return List.of(new OrderItemRequest(productName, quantity, price));
        }
        return List.of();
    }
}
