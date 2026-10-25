package com.daniellaera.notificationservice.adapter.in.messaging;

import java.math.BigDecimal;

public record OrderItemEvent(
    String productName,
    Integer quantity,
    BigDecimal price,
    BigDecimal totalAmount
) {}
