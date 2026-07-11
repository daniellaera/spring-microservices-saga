package com.daniellaera.cartservice.model;

import java.math.BigDecimal;

public record CartItem(String productName, Integer quantity, BigDecimal price) {}
