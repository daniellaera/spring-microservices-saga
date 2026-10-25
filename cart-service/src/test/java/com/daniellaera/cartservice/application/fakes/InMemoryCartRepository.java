package com.daniellaera.cartservice.application.fakes;

import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;
import com.daniellaera.cartservice.domain.port.out.CartRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Carts in memory; every write records the lifetime it was given, as the time left. */
public class InMemoryCartRepository implements CartRepository {

    private final Map<String, Map<String, CartItem>> carts = new HashMap<>();
    private final Map<String, Long> timeLeft = new HashMap<>();
    private final List<String> writes = new ArrayList<>();

    @Override
    public Optional<Cart> find(String userEmail) {
        Map<String, CartItem> items = carts.get(userEmail);
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Cart(userEmail, List.copyOf(items.values()), timeLeft.getOrDefault(userEmail, 0L), true));
    }

    @Override
    public Optional<CartItem> findItem(String userEmail, String productName) {
        return Optional.ofNullable(carts.getOrDefault(userEmail, Map.of()).get(productName));
    }

    @Override
    public void saveItem(String userEmail, CartItem item, Duration lifetime) {
        carts.computeIfAbsent(userEmail, email -> new LinkedHashMap<>()).put(item.productName(), item);
        timeLeft.put(userEmail, lifetime.toSeconds());
        writes.add("save " + item.productName() + " " + lifetime.toSeconds());
    }

    @Override
    public void removeItem(String userEmail, String productName, Duration lifetime) {
        carts.getOrDefault(userEmail, new LinkedHashMap<>()).remove(productName);
        timeLeft.put(userEmail, lifetime.toSeconds());
        writes.add("remove " + productName + " " + lifetime.toSeconds());
    }

    @Override
    public void delete(String userEmail) {
        carts.remove(userEmail);
        timeLeft.remove(userEmail);
        writes.add("delete");
    }

    public List<String> writes() {
        return List.copyOf(writes);
    }
}
