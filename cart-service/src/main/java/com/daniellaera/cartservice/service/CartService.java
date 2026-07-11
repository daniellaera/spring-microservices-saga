package com.daniellaera.cartservice.service;

import com.daniellaera.cartservice.dto.AddToCartRequest;
import com.daniellaera.cartservice.dto.CartResponse;
import com.daniellaera.cartservice.model.CartItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CART_PREFIX = "cart:";
    private static final long CART_TTL_SECONDS = 1800;

    private String cartKey(String userEmail) {
        return CART_PREFIX + userEmail;
    }

    public CartResponse getCart(String userEmail) {
        String key = cartKey(userEmail);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return new CartResponse(userEmail, List.of(), BigDecimal.ZERO, 0, "Cart is empty");
        }

        List<CartItem> items = entries.values().stream()
                .map(v -> {
                    try {
                        return objectMapper.convertValue(v, CartItem.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        BigDecimal total = items.stream()
                .map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        String expiresIn = formatTtl(ttl != null ? ttl : 0);

        return new CartResponse(userEmail, items, total, ttl != null ? ttl : 0, expiresIn);
    }

    public CartResponse addItem(String userEmail, AddToCartRequest request) {
        String key = cartKey(userEmail);

        CartItem item = new CartItem(request.productName(), request.quantity(), request.price());

        Object existing = redisTemplate.opsForHash().get(key, request.productName());
        if (existing != null) {
            CartItem existingItem = objectMapper.convertValue(existing, CartItem.class);
            item = new CartItem(request.productName(), existingItem.quantity() + request.quantity(), request.price());
        }

        redisTemplate.opsForHash().put(key, request.productName(), item);
        redisTemplate.expire(key, CART_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("=== Cart: added {} x{} for {}", request.productName(), request.quantity(), userEmail);

        return getCart(userEmail);
    }

    public CartResponse removeItem(String userEmail, String productName) {
        String key = cartKey(userEmail);
        redisTemplate.opsForHash().delete(key, productName);
        redisTemplate.expire(key, CART_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("=== Cart: removed {} for {}", productName, userEmail);
        return getCart(userEmail);
    }

    public CartResponse updateQuantity(String userEmail, String productName, Integer quantity) {
        String key = cartKey(userEmail);
        Object existing = redisTemplate.opsForHash().get(key, productName);

        if (existing == null) {
            return getCart(userEmail);
        }

        if (quantity <= 0) {
            return removeItem(userEmail, productName);
        }

        CartItem existingItem = objectMapper.convertValue(existing, CartItem.class);
        CartItem updated = new CartItem(productName, quantity, existingItem.price());
        redisTemplate.opsForHash().put(key, productName, updated);
        redisTemplate.expire(key, CART_TTL_SECONDS, TimeUnit.SECONDS);

        return getCart(userEmail);
    }

    public void clearCart(String userEmail) {
        redisTemplate.delete(cartKey(userEmail));
        log.info("=== Cart: cleared for {}", userEmail);
    }

    private String formatTtl(long seconds) {
        if (seconds <= 0) return "Expired";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
