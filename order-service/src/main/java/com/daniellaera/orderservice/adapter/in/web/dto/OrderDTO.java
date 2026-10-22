package com.daniellaera.orderservice.adapter.in.web.dto;

import com.daniellaera.orderservice.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDTO(Long id, String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount, OrderStatus status, String userEmail, LocalDateTime createdAt, String paymentIntentId, List<OrderItemDTO> items) {}
