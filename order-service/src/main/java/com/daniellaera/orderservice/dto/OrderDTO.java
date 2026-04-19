package com.daniellaera.orderservice.dto;

import com.daniellaera.orderservice.enums.OrderStatus;

public record OrderDTO(Long id, String productName, Integer quantity, OrderStatus status) {}
