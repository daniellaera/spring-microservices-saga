package com.daniellaera.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemDTO(
    Long id,
    String productName,
    Integer quantity,
    BigDecimal price,
    BigDecimal totalAmount
) {}
