package com.daniellaera.cartservice.adapter.in.web.dto;


import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    String userEmail,
    List<CartItemResponse> items,
    BigDecimal total,
    long ttlSeconds,
    String expiresIn
) {}
