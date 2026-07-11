package com.daniellaera.inventoryservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderEvent(Long orderId, String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount, String userEmail, String paymentIntentId, List<OrderItemEvent> items) {}