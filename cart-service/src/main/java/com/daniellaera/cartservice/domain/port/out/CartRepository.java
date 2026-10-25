package com.daniellaera.cartservice.domain.port.out;

import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;

import java.time.Duration;
import java.util.Optional;

/** Where carts live. A change restarts the cart's lifetime; an expired cart is gone. */
public interface CartRepository {

    // empty when nothing is stored for the customer; items that cannot be read are left out
    Optional<Cart> find(String userEmail);

    Optional<CartItem> findItem(String userEmail, String productName);

    void saveItem(String userEmail, CartItem item, Duration lifetime);

    void removeItem(String userEmail, String productName, Duration lifetime);

    void delete(String userEmail);
}
