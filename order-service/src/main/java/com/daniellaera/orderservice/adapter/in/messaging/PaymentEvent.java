package com.daniellaera.orderservice.adapter.in.messaging;

import java.math.BigDecimal;

public record PaymentEvent(Long orderId, String productName, Integer quantity, String status, BigDecimal price, BigDecimal totalAmount, String userEmail) {}
