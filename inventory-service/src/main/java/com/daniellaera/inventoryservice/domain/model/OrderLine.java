package com.daniellaera.inventoryservice.domain.model;

import java.math.BigDecimal;

/** One item of an order, as received on orders-topic. */
public record OrderLine(String productName, Integer quantity, BigDecimal price, BigDecimal totalAmount) {
}
