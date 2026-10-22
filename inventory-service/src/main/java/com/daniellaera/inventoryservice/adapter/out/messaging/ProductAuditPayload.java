package com.daniellaera.inventoryservice.adapter.out.messaging;

import com.daniellaera.inventoryservice.domain.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PRODUCT_* audit payload. It used to be the JPA entity serialized as-is, which Jackson writes with its
 * properties in alphabetical order; the components are declared in that order to keep the JSON identical.
 */
record ProductAuditPayload(LocalDateTime createdAt, Long id, String name, BigDecimal price, Integer quantity) {

    static ProductAuditPayload of(Product product) {
        return new ProductAuditPayload(product.createdAt(), product.id(), product.name(), product.price(),
                product.quantity());
    }
}
