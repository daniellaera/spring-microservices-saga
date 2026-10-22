package com.daniellaera.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void create_hasNoIdNorCreatedAt() {
        Product product = Product.create("MacBook Pro", 10, new BigDecimal("1299.99"));

        assertThat(product.id()).isNull();
        assertThat(product.createdAt()).isNull();
        assertThat(product.name()).isEqualTo("MacBook Pro");
        assertThat(product.quantity()).isEqualTo(10);
        assertThat(product.price()).isEqualByComparingTo("1299.99");
    }

    @Test
    void restore_keepsEveryField() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 9, 30);

        Product product = Product.restore(11L, "MacBook Pro", 10, new BigDecimal("1299.99"), createdAt);

        assertThat(product.id()).isEqualTo(11L);
        assertThat(product.createdAt()).isEqualTo(createdAt);
        assertThat(product.quantity()).isEqualTo(10);
    }

    @Test
    void canReserve_upToTheStockOnHand() {
        Product product = Product.create("MacBook Pro", 10, BigDecimal.ONE);

        assertThat(product.canReserve(9)).isTrue();
        assertThat(product.canReserve(10)).isTrue();
        assertThat(product.canReserve(11)).isFalse();
    }

    @Test
    void deduct_subtractsTheRequestedQuantity() {
        Product product = Product.create("MacBook Pro", 10, BigDecimal.ONE);

        product.deduct(3);

        assertThat(product.quantity()).isEqualTo(7);
    }

    @Test
    void deduct_hasNoGuardAgainstNegativeStock() {
        // callers check canReserve first; the arithmetic itself is unguarded (current behaviour)
        Product product = Product.create("MacBook Pro", 1, BigDecimal.ONE);

        product.deduct(3);

        assertThat(product.quantity()).isEqualTo(-2);
    }

    @Test
    void release_addsTheReservedQuantityBack() {
        Product product = Product.create("MacBook Pro", 7, BigDecimal.ONE);

        product.release(3);

        assertThat(product.quantity()).isEqualTo(10);
    }

    @Test
    void restock_addsTheQuantity() {
        Product product = Product.create("MacBook Pro", 10, BigDecimal.ONE);

        product.restock(5);

        assertThat(product.quantity()).isEqualTo(15);
    }
}
