package com.daniellaera.cartservice.dto;

import com.daniellaera.cartservice.model.CartItem;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    String userEmail,
    List<CartItem> items,
    BigDecimal total,
    long ttlSeconds,
    String expiresIn
) {}
