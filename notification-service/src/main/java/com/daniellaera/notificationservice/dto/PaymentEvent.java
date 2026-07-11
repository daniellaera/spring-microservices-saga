package com.daniellaera.notificationservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaymentEvent(
        Long orderId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        String status,
        String userEmail,
        List<OrderItemEvent> items
) {}