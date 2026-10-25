package com.daniellaera.cartservice.adapter.out.redis;

import com.daniellaera.cartservice.TestcontainersConfiguration;
import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The adapter on a real Redis: the hash per cart, its expiry, and values that cannot be read. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisCartRepositoryITTest {

    private static final String USER = "redis-it@example.com";
    private static final String KEY = "cart:" + USER;

    @Autowired
    private RedisCartRepository repository;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void emptyCart() {
        redis.delete(KEY);
    }

    @Test
    void nothingStored_isNoCart() {
        assertThat(repository.find(USER)).isEmpty();
        assertThat(repository.findItem(USER, "Lamp")).isEmpty();
    }

    @Test
    void savedItems_areTheCart_withTheTimeLeft() {
        repository.saveItem(USER, new CartItem("Lamp", 2, new BigDecimal("49.90")), Duration.ofSeconds(1800));

        assertThat(repository.find(USER)).hasValueSatisfying(cart -> {
            assertThat(cart.stored()).isTrue();
            assertThat(cart.items()).extracting(CartItem::productName, CartItem::quantity)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("Lamp", 2));
            assertThat(cart.ttlSeconds()).isBetween(1795L, 1800L);
        });
        assertThat(repository.findItem(USER, "Lamp")).hasValueSatisfying(item ->
                assertThat(item.price()).isEqualByComparingTo("49.90"));
        assertThat(redis.getExpire(KEY)).isBetween(1795L, 1800L);
    }

    @Test
    void aValueThatIsNotAnItem_isLeftOut_ofAStoredCart() {
        repository.saveItem(USER, new CartItem("Lamp", 1, new BigDecimal("49.90")), Duration.ofSeconds(1800));
        redis.opsForHash().put(KEY, "Broken", "42");

        assertThat(repository.find(USER)).hasValueSatisfying(cart ->
                assertThat(cart.items()).extracting(CartItem::productName).containsExactly("Lamp"));
    }

    @Test
    void removingAnItem_restartsTheLifetime_andDeletingRemovesTheCart() {
        repository.saveItem(USER, new CartItem("Lamp", 1, new BigDecimal("49.90")), Duration.ofSeconds(1800));
        repository.saveItem(USER, new CartItem("Desk", 1, new BigDecimal("199.00")), Duration.ofSeconds(1800));
        redis.expire(KEY, Duration.ofSeconds(100));

        repository.removeItem(USER, "Lamp", Duration.ofSeconds(1800));

        assertThat(redis.getExpire(KEY)).isGreaterThan(1700L);
        assertThat(repository.find(USER)).hasValueSatisfying(cart ->
                assertThat(cart.items()).extracting(CartItem::productName).containsExactly("Desk"));

        repository.delete(USER);

        assertThat(redis.hasKey(KEY)).isFalse();
        assertThat(repository.find(USER)).isEmpty();
    }
}
