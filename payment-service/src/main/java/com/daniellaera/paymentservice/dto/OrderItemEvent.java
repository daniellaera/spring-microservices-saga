package com.daniellaera.paymentservice.dto;

import java.math.BigDecimal;

public record OrderItemEvent(
    String productName,
    Integer quantity,
    BigDecimal price,
    BigDecimal totalAmount
) {}
