package com.daniellaera.orderservice.dto;

import java.math.BigDecimal;

public record PaymentEvent(Long orderId, String productName, Integer quantity, String status, BigDecimal price, BigDecimal totalAmount) {}
