package com.daniellaera.paymentservice.adapter.out.messaging;

import java.math.BigDecimal;

public record OrderItemEvent(
    String productName,
    Integer quantity,
    BigDecimal price,
    BigDecimal totalAmount
) {}
