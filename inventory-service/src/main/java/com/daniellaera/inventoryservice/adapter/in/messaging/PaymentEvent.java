package com.daniellaera.inventoryservice.adapter.in.messaging;

import java.math.BigDecimal;
import java.util.List;

public record PaymentEvent(Long orderId, String productName, Integer quantity, String status, BigDecimal price, BigDecimal totalAmount, String userEmail, List<OrderItemEvent> items) {}
