package com.daniellaera.notificationservice.dto;

import java.math.BigDecimal;

public record PaymentEvent(
        Long orderId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal totalAmount,
        String status,
        String userEmail
) {}