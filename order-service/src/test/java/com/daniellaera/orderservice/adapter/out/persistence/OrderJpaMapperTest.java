package com.daniellaera.orderservice.adapter.out.persistence;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderJpaMapperTest {

    @Test
    void toNewEntity_mapsOrderLegacyColumnsAndItemsWithBackReference() {
        Order order = Order.place("user@test.com", "pi_123", List.of(
                OrderItem.of("iPhone 16", 2, new BigDecimal("999.99")),
                OrderItem.of("AirPods", 1, new BigDecimal("249.99"))));

        OrderJpaEntity entity = OrderJpaMapper.toNewEntity(order);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getUserEmail()).isEqualTo("user@test.com");
        assertThat(entity.getPaymentIntentId()).isEqualTo("pi_123");
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(entity.getTotalAmount()).isEqualByComparingTo("2249.97");
        assertThat(entity.getProductName()).isEqualTo("iPhone 16");
        assertThat(entity.getQuantity()).isEqualTo(2);
        assertThat(entity.getPrice()).isEqualByComparingTo("999.99");
        assertThat(entity.getItems()).hasSize(2);
        assertThat(entity.getItems()).allSatisfy(item -> assertThat(item.getOrder()).isSameAs(entity));
        OrderItemJpaEntity first = entity.getItems().get(0);
        assertThat(first.getProductName()).isEqualTo("iPhone 16");
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getPrice()).isEqualByComparingTo("999.99");
        assertThat(first.getTotalAmount()).isEqualByComparingTo("1999.98");
    }

    @Test
    void toDomain_mapsEntityAndItems() {
        OrderJpaEntity entity = OrderJpaMapper.toNewEntity(Order.place("user@test.com", "pi_123", List.of(
                OrderItem.of("iPhone 16", 2, new BigDecimal("999.99")))));
        entity.setStatus(OrderStatus.CONFIRMED);

        Order order = OrderJpaMapper.toDomain(entity);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.userEmail()).isEqualTo("user@test.com");
        assertThat(order.paymentIntentId()).isEqualTo("pi_123");
        assertThat(order.totalAmount()).isEqualByComparingTo("1999.98");
        assertThat(order.items()).containsExactly(
                new OrderItem(null, "iPhone 16", 2, new BigDecimal("999.99"), new BigDecimal("1999.98")));
    }

    @Test
    void toDomain_legacyOrderWithoutItems_keepsLegacyColumns() {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setProductName("MacBook Pro");
        entity.setQuantity(1);
        entity.setPrice(new BigDecimal("1299.99"));
        entity.setTotalAmount(new BigDecimal("1299.99"));
        entity.setStatus(OrderStatus.PENDING);
        entity.setUserEmail("user@test.com");

        Order order = OrderJpaMapper.toDomain(entity);

        assertThat(order.items()).isEmpty();
        assertThat(order.productName()).isEqualTo("MacBook Pro");
        assertThat(order.quantity()).isEqualTo(1);
        assertThat(order.price()).isEqualByComparingTo("1299.99");
        assertThat(order.totalAmount()).isEqualByComparingTo("1299.99");
    }

    @Test
    void toDomain_nullItems_mapsToEmptyList() {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setProductName("MacBook Pro");
        entity.setQuantity(1);
        entity.setStatus(OrderStatus.PENDING);
        entity.setItems(null);

        assertThat(OrderJpaMapper.toDomain(entity).items()).isEmpty();
    }
}
