package com.daniellaera.cartservice.adapter.in.web.dto;

import java.math.BigDecimal;

public record CartItemResponse(String productName, Integer quantity, BigDecimal price) {}
