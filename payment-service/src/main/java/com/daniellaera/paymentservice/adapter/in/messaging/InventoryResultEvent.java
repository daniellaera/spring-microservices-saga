package com.daniellaera.paymentservice.adapter.in.messaging;

import java.math.BigDecimal;
import java.util.List;

public record InventoryResultEvent(
        Long orderId,
        String status,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        String userEmail,
        String paymentIntentId,
        List<OrderItemEvent> items
) {}
