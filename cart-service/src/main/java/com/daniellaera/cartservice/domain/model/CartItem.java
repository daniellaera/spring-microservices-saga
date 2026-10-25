package com.daniellaera.cartservice.domain.model;

import java.math.BigDecimal;

/** One product in a cart: the quantity wanted and the unit price it was added at. */
public record CartItem(String productName, Integer quantity, BigDecimal price) {

    public BigDecimal lineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
