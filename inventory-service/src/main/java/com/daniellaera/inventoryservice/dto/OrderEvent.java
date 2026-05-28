package com.daniellaera.inventoryservice.dto;

import java.math.BigDecimal;

public record OrderEvent(Long orderId, String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount, String userEmail) {}