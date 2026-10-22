package com.daniellaera.orderservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private static final List<OrderItem> ITEMS = List.of(
            OrderItem.of("iPhone 16", 2, new BigDecimal("999.99")),
            OrderItem.of("AirPods", 1, new BigDecimal("249.99")));

    @Test
    void orderItem_of_computesLineTotal() {
        OrderItem item = OrderItem.of("Case", 3, new BigDecimal("19.99"));

        assertThat(item.id()).isNull();
        assertThat(item.totalAmount()).isEqualByComparingTo("59.97");
    }

    @Test
    void place_sumsItemTotalsAndStartsPending() {
        Order order = Order.place("user@test.com", "pi_123", ITEMS);

        assertThat(order.totalAmount()).isEqualByComparingTo("2249.97");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.userEmail()).isEqualTo("user@test.com");
        assertThat(order.paymentIntentId()).isEqualTo("pi_123");
        assertThat(order.items()).containsExactlyElementsOf(ITEMS);
        assertThat(order.id()).isNull();
        assertThat(order.createdAt()).isNull();
    }

    @Test
    void place_withoutItems_throwsEmptyOrderException() {
        assertThatThrownBy(() -> Order.place("user@test.com", null, List.of()))
                .isInstanceOf(EmptyOrderException.class)
                .hasMessage("Order must have at least one item");
        assertThatThrownBy(() -> Order.place("user@test.com", null, null))
                .isInstanceOf(EmptyOrderException.class);
    }

    @Test
    void place_legacySingleItemViewIsTheFirstItem() {
        Order order = Order.place("user@test.com", null, ITEMS);

        assertThat(order.productName()).isEqualTo("iPhone 16");
        assertThat(order.quantity()).isEqualTo(2);
        assertThat(order.price()).isEqualByComparingTo("999.99");
    }

    @Test
    void place_acceptsMissingPaymentIntentAndEmail() {
        Order order = Order.place(null, null, ITEMS);

        assertThat(order.userEmail()).isNull();
        assertThat(order.paymentIntentId()).isNull();
    }

    @Test
    void items_areUnmodifiable() {
        Order order = Order.place("user@test.com", null, ITEMS);

        assertThatThrownBy(() -> order.items().add(OrderItem.of("Extra", 1, BigDecimal.ONE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void restore_keepsPersistedLegacySnapshotForOrdersWithoutItems() {
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 15, 10, 30);

        Order order = Order.restore(7L, "user@test.com", null, OrderStatus.CONFIRMED,
                new BigDecimal("1299.99"), createdAt, "MacBook Pro", 1, new BigDecimal("1299.99"), List.of());

        assertThat(order.id()).isEqualTo(7L);
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.createdAt()).isEqualTo(createdAt);
        assertThat(order.items()).isEmpty();
        assertThat(order.productName()).isEqualTo("MacBook Pro");
        assertThat(order.quantity()).isEqualTo(1);
        assertThat(order.price()).isEqualByComparingTo("1299.99");
    }

    @Test
    void applyPaymentResult_success_confirms() {
        Order order = Order.place("user@test.com", null, ITEMS);

        order.applyPaymentResult(true);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void applyPaymentResult_failure_cancels() {
        Order order = Order.place("user@test.com", null, ITEMS);

        order.applyPaymentResult(false);

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void applyPaymentResult_hasNoTransitionGuard() {
        Order order = Order.place("user@test.com", null, ITEMS);

        order.applyPaymentResult(true);
        order.applyPaymentResult(false);

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void ensureReadableBy_owner() {
        Order order = Order.place("user@test.com", null, ITEMS);

        assertThatCode(() -> order.ensureReadableBy("user@test.com", false)).doesNotThrowAnyException();
    }

    @Test
    void ensureReadableBy_admin() {
        Order order = Order.place("user@test.com", null, ITEMS);

        assertThatCode(() -> order.ensureReadableBy("admin@test.com", true)).doesNotThrowAnyException();
    }

    @Test
    void ensureReadableBy_otherUser_isDenied() {
        Order order = Order.place("user@test.com", null, ITEMS);

        assertThatThrownBy(() -> order.ensureReadableBy("other@test.com", false))
                .isInstanceOf(OrderAccessDeniedException.class)
                .hasMessage("Access denied");
        assertThatThrownBy(() -> order.ensureReadableBy(null, false))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void ensureReadableBy_orderWithoutEmail_throwsNullPointerForNonAdmin() {
        Order order = Order.place(null, null, ITEMS);

        assertThatThrownBy(() -> order.ensureReadableBy("user@test.com", false))
                .isInstanceOf(NullPointerException.class);
        assertThatCode(() -> order.ensureReadableBy("user@test.com", true)).doesNotThrowAnyException();
    }

    @Test
    void notFoundException_message() {
        assertThat(new OrderNotFoundException(999L)).hasMessage("Order not found with id: 999");
    }
}
