package com.daniellaera.cartservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CartTest {

    private static final List<CartItem> ITEMS = List.of(
            new CartItem("MacBook Pro", 2, new BigDecimal("1299.99")),
            new CartItem("USB-C Hub", 2, new BigDecimal("49.90")));

    @Test
    void theTotal_isTheSumOfEveryLine() {
        assertThat(new Cart("a@b.c", ITEMS, 1800, true).total()).isEqualByComparingTo("2699.78");
        assertThat(new CartItem("Lamp", 3, new BigDecimal("49.90")).lineTotal()).isEqualByComparingTo("149.70");
    }

    @Test
    void anEmptyCart_hasNothingAndSaysSo() {
        Cart empty = Cart.empty("a@b.c");

        assertThat(empty.items()).isEmpty();
        assertThat(empty.total()).isEqualTo(BigDecimal.ZERO);
        assertThat(empty.ttlSeconds()).isZero();
        assertThat(empty.expiresIn()).isEqualTo("Cart is empty");
    }

    @Test
    void theTimeLeft_inMinutesAndSeconds() {
        assertThat(new Cart("a@b.c", ITEMS, 1799, true).expiresIn()).isEqualTo("29m 59s");
        assertThat(new Cart("a@b.c", ITEMS, 60, true).expiresIn()).isEqualTo("1m 0s");
        assertThat(new Cart("a@b.c", ITEMS, 45, true).expiresIn()).isEqualTo("45s");
        assertThat(new Cart("a@b.c", ITEMS, 0, true).expiresIn()).isEqualTo("Expired");
        assertThat(new Cart("a@b.c", List.of(), -2, true).expiresIn()).isEqualTo("Expired");
    }

    @Test
    void aCartLivesHalfAnHourAfterItsLastChange() {
        assertThat(Cart.LIFETIME.toSeconds()).isEqualTo(1800);
    }
}
