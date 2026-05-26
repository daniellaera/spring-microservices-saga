package com.daniellaera.orderservice.dto;

import com.daniellaera.orderservice.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDTO(Long id, String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount, OrderStatus status, String userEmail, LocalDateTime createdAt) {}
