package com.daniellaera.cartservice.service;

import com.daniellaera.cartservice.dto.AddToCartRequest;
import com.daniellaera.cartservice.dto.CartResponse;
import com.daniellaera.cartservice.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ObjectMapper objectMapper;
    private CartService cartService;

    private static final String USER_EMAIL = "user@test.com";
    private static final String CART_KEY = "cart:" + USER_EMAIL;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cartService = new CartService(redisTemplate, objectMapper);
    }

    @Test
    void getCart_returnsEmptyCart_whenRedisHasNoEntries() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(CART_KEY)).thenReturn(Map.of());

        CartResponse response = cartService.getCart(USER_EMAIL);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.userEmail()).isEqualTo(USER_EMAIL);
    }

    @Test
    void addItem_storesItemInRedis_andReturnsCartWithItem() {
        AddToCartRequest request = new AddToCartRequest("MacBook Pro", 1, BigDecimal.valueOf(999.99));
        CartItem stored = new CartItem("MacBook Pro", 1, BigDecimal.valueOf(999.99));

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(CART_KEY, "MacBook Pro")).thenReturn(null);
        when(hashOperations.entries(CART_KEY)).thenReturn(Map.of("MacBook Pro", stored));
        when(redisTemplate.getExpire(CART_KEY, TimeUnit.SECONDS)).thenReturn(1800L);

        CartResponse response = cartService.addItem(USER_EMAIL, request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("MacBook Pro");
        verify(hashOperations).put(eq(CART_KEY), eq("MacBook Pro"), any(CartItem.class));
        verify(redisTemplate).expire(eq(CART_KEY), eq(1800L), eq(TimeUnit.SECONDS));
    }

    @Test
    void addItem_updatesQuantity_whenItemAlreadyExists() {
        AddToCartRequest request = new AddToCartRequest("MacBook Pro", 2, BigDecimal.valueOf(999.99));
        CartItem existingItem = new CartItem("MacBook Pro", 1, BigDecimal.valueOf(999.99));
        CartItem merged = new CartItem("MacBook Pro", 3, BigDecimal.valueOf(999.99));

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(CART_KEY, "MacBook Pro")).thenReturn(existingItem);
        when(hashOperations.entries(CART_KEY)).thenReturn(Map.of("MacBook Pro", merged));
        when(redisTemplate.getExpire(CART_KEY, TimeUnit.SECONDS)).thenReturn(1800L);

        CartResponse response = cartService.addItem(USER_EMAIL, request);

        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        verify(hashOperations).put(eq(CART_KEY), eq("MacBook Pro"),
                eq(new CartItem("MacBook Pro", 3, BigDecimal.valueOf(999.99))));
    }

    @Test
    void removeItem_deletesFromRedis() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(CART_KEY)).thenReturn(Map.of());

        cartService.removeItem(USER_EMAIL, "MacBook Pro");

        verify(hashOperations).delete(CART_KEY, "MacBook Pro");
        verify(redisTemplate).expire(eq(CART_KEY), eq(1800L), eq(TimeUnit.SECONDS));
    }

    @Test
    void clearCart_deletesTheKey() {
        cartService.clearCart(USER_EMAIL);

        verify(redisTemplate).delete(CART_KEY);
    }

    @Test
    void updateQuantity_withZero_callsRemoveItem() {
        CartItem existingItem = new CartItem("MacBook Pro", 1, BigDecimal.valueOf(999.99));

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(CART_KEY, "MacBook Pro")).thenReturn(existingItem);
        when(hashOperations.entries(CART_KEY)).thenReturn(Map.of());

        cartService.updateQuantity(USER_EMAIL, "MacBook Pro", 0);

        verify(hashOperations).delete(CART_KEY, "MacBook Pro");
        verify(hashOperations, never()).put(eq(CART_KEY), eq("MacBook Pro"), any(CartItem.class));
    }

    @Test
    void ttlIsSetOnEveryWriteOperation() {
        AddToCartRequest request = new AddToCartRequest("MacBook Pro", 1, BigDecimal.valueOf(999.99));
        CartItem existingItem = new CartItem("MacBook Pro", 1, BigDecimal.valueOf(999.99));

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), anyString())).thenReturn(existingItem);
        when(hashOperations.entries(CART_KEY)).thenReturn(Map.of("MacBook Pro", existingItem));
        when(redisTemplate.getExpire(CART_KEY, TimeUnit.SECONDS)).thenReturn(1800L);

        cartService.addItem(USER_EMAIL, request);
        cartService.updateQuantity(USER_EMAIL, "MacBook Pro", 2);
        cartService.removeItem(USER_EMAIL, "MacBook Pro");

        verify(redisTemplate, times(3)).expire(eq(CART_KEY), anyLong(), eq(TimeUnit.SECONDS));
    }
}
