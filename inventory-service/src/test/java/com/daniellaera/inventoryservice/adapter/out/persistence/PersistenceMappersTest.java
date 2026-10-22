package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMappersTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 15, 9, 30);

    @Test
    void product_toDomain_copiesEveryField() {
        ProductJpaEntity entity = new ProductJpaEntity();
        ReflectionTestUtils.setField(entity, "id", 11L);
        ReflectionTestUtils.setField(entity, "createdAt", AT);
        entity.setName("MacBook Pro");
        entity.setQuantity(10);
        entity.setPrice(new BigDecimal("1299.99"));

        Product product = ProductJpaMapper.toDomain(entity);

        assertThat(product.id()).isEqualTo(11L);
        assertThat(product.createdAt()).isEqualTo(AT);
        assertThat(product.name()).isEqualTo("MacBook Pro");
        assertThat(product.quantity()).isEqualTo(10);
        assertThat(product.price()).isEqualByComparingTo("1299.99");
    }

    @Test
    void product_toNewEntity_leavesIdAndCreatedAtToJpa() {
        ProductJpaEntity entity = ProductJpaMapper.toNewEntity(Product.create("USB-C Hub", 50, new BigDecimal("49.90")));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getName()).isEqualTo("USB-C Hub");
        assertThat(entity.getQuantity()).isEqualTo(50);
        assertThat(entity.getPrice()).isEqualByComparingTo("49.90");
    }

    @Test
    void reservation_toDomain_copiesEveryField() {
        UUID id = UUID.randomUUID();
        StockReservationJpaEntity entity = new StockReservationJpaEntity();
        entity.setId(id);
        entity.setOrderId(42L);
        entity.setProductName("MacBook Pro");
        entity.setQuantity(2);
        entity.setReservedAt(AT);

        assertThat(StockReservationJpaMapper.toDomain(entity))
                .isEqualTo(new StockReservation(id, 42L, "MacBook Pro", 2, AT));
    }

    @Test
    void reservation_toNewEntity_withoutReservedAt_keepsTheEntityDefault() {
        LocalDateTime before = LocalDateTime.now();

        StockReservationJpaEntity entity = StockReservationJpaMapper.toNewEntity(StockReservation.of(42L, "MacBook Pro", 2));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getOrderId()).isEqualTo(42L);
        assertThat(entity.getProductName()).isEqualTo("MacBook Pro");
        assertThat(entity.getQuantity()).isEqualTo(2);
        assertThat(entity.getReservedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void reservation_toNewEntity_withReservedAt_keepsIt() {
        StockReservationJpaEntity entity = StockReservationJpaMapper.toNewEntity(
                new StockReservation(null, 42L, "MacBook Pro", 2, AT));

        assertThat(entity.getReservedAt()).isEqualTo(AT);
    }
}
