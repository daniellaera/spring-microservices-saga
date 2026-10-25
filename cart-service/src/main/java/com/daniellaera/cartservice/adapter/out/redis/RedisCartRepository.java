package com.daniellaera.cartservice.adapter.out.redis;

import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;
import com.daniellaera.cartservice.domain.port.out.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A cart is the Redis hash "cart:<email>": one field per product, its value the StoredCartItem JSON. */
@Component
@RequiredArgsConstructor
public class RedisCartRepository implements CartRepository {

    private static final String CART_PREFIX = "cart:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private String cartKey(String userEmail) {
        return CART_PREFIX + userEmail;
    }

    @Override
    public Optional<Cart> find(String userEmail) {
        String key = cartKey(userEmail);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return Optional.empty();
        }

        List<CartItem> items = entries.values().stream()
                .map(v -> {
                    try {
                        return toItem(objectMapper.convertValue(v, StoredCartItem.class));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return Optional.of(new Cart(userEmail, items, ttl != null ? ttl : 0, true));
    }

    @Override
    public Optional<CartItem> findItem(String userEmail, String productName) {
        Object existing = redisTemplate.opsForHash().get(cartKey(userEmail), productName);
        if (existing == null) {
            return Optional.empty();
        }
        return Optional.of(toItem(objectMapper.convertValue(existing, StoredCartItem.class)));
    }

    @Override
    public void saveItem(String userEmail, CartItem item, Duration lifetime) {
        String key = cartKey(userEmail);
        redisTemplate.opsForHash().put(key, item.productName(),
                new StoredCartItem(item.productName(), item.quantity(), item.price()));
        redisTemplate.expire(key, lifetime.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void removeItem(String userEmail, String productName, Duration lifetime) {
        String key = cartKey(userEmail);
        redisTemplate.opsForHash().delete(key, productName);
        redisTemplate.expire(key, lifetime.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void delete(String userEmail) {
        redisTemplate.delete(cartKey(userEmail));
    }

    private static CartItem toItem(StoredCartItem stored) {
        return new CartItem(stored.productName(), stored.quantity(), stored.price());
    }
}
