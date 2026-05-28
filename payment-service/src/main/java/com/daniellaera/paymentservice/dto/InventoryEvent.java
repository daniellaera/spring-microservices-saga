package com.daniellaera.paymentservice.dto;

import java.math.BigDecimal;

public record InventoryEvent(Long orderId, String productName, Integer quantity, String status, BigDecimal price, BigDecimal totalAmount, String userEmail) {}
