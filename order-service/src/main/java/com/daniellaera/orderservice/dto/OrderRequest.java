package com.daniellaera.orderservice.dto;

import java.math.BigDecimal;

public record OrderRequest(String productName, Integer quantity, BigDecimal price) {}
