package com.daniellaera.orderservice.adapter.in.web.dto;

import java.math.BigDecimal;

public record OrderItemDTO(
    Long id,
    String productName,
    Integer quantity,
    BigDecimal price,
    BigDecimal totalAmount
) {}
