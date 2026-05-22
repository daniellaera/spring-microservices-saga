package com.daniellaera.frontendservice.dto;

import java.math.BigDecimal;

public record OrderDto(Long id, String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount, String status) {}
