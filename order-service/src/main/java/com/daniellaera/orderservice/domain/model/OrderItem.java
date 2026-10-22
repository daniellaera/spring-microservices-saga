package com.daniellaera.orderservice.domain.model;

import java.math.BigDecimal;

public record OrderItem(Long id, String productName, int quantity, BigDecimal price, BigDecimal totalAmount) {

    public static OrderItem of(String productName, int quantity, BigDecimal price) {
        return new OrderItem(null, productName, quantity, price, price.multiply(BigDecimal.valueOf(quantity)));
    }
}
