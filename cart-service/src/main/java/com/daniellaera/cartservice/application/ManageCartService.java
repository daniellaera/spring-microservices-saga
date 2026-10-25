package com.daniellaera.cartservice.application;

import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;
import com.daniellaera.cartservice.domain.port.in.AddToCartUseCase;
import com.daniellaera.cartservice.domain.port.in.ClearCartUseCase;
import com.daniellaera.cartservice.domain.port.in.GetCartQuery;
import com.daniellaera.cartservice.domain.port.in.RemoveFromCartUseCase;
import com.daniellaera.cartservice.domain.port.in.UpdateCartItemUseCase;
import com.daniellaera.cartservice.domain.port.out.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Every cart operation; each change restarts the cart's lifetime, and the cart is answered as it is afterwards. */
public class ManageCartService implements GetCartQuery, AddToCartUseCase, UpdateCartItemUseCase,
        RemoveFromCartUseCase, ClearCartUseCase {

    private static final Logger log = LoggerFactory.getLogger(ManageCartService.class);

    private final CartRepository cartRepository;

    public ManageCartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Override
    public Cart getCart(String userEmail) {
        return cartRepository.find(userEmail).orElseGet(() -> Cart.empty(userEmail));
    }

    @Override
    public Cart addItem(String userEmail, CartItem item) {
        CartItem toStore = item;
        Optional<CartItem> existing = cartRepository.findItem(userEmail, item.productName());
        if (existing.isPresent()) {
            toStore = new CartItem(item.productName(), existing.get().quantity() + item.quantity(), item.price());
        }

        cartRepository.saveItem(userEmail, toStore, Cart.LIFETIME);

        log.info("=== Cart: added {} x{} for {}", item.productName(), item.quantity(), userEmail);

        return getCart(userEmail);
    }

    @Override
    public Cart removeItem(String userEmail, String productName) {
        cartRepository.removeItem(userEmail, productName, Cart.LIFETIME);
        log.info("=== Cart: removed {} for {}", productName, userEmail);
        return getCart(userEmail);
    }

    @Override
    public Cart updateQuantity(String userEmail, String productName, Integer quantity) {
        Optional<CartItem> existing = cartRepository.findItem(userEmail, productName);

        if (existing.isEmpty()) {
            return getCart(userEmail);
        }

        if (quantity <= 0) {
            return removeItem(userEmail, productName);
        }

        cartRepository.saveItem(userEmail, new CartItem(productName, quantity, existing.get().price()), Cart.LIFETIME);

        return getCart(userEmail);
    }

    @Override
    public void clearCart(String userEmail) {
        cartRepository.delete(userEmail);
        log.info("=== Cart: cleared for {}", userEmail);
    }
}
